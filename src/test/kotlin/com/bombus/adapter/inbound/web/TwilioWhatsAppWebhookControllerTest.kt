package com.bombus.adapter.inbound.web

import com.bombus.config.BombusApplication
import com.bombus.config.SecurityConfig
import com.bombus.config.TwilioConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@WebMvcTest(TwilioWhatsAppWebhookController::class)
@ContextConfiguration(classes = [BombusApplication::class])
@Import(
    TwilioConfig::class,
    SecurityConfig::class,
    TwilioSignatureValidator::class,
    StaticWhatsAppResponder::class,
    WebExceptionHandler::class,
)
@TestPropertySource(
    properties = [
        "twilio.auth-token=test-auth-token",
        "twilio.public-base-url=https://example.test",
    ],
)
class TwilioWhatsAppWebhookControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `valid signature returns 200 with TwiML reply`() {
        val params = linkedMapOf("From" to "whatsapp:+5511999999999", "Body" to "oi")
        val signature = twilioSignature(SIGNED_URL, params)

        mockMvc.post(WEBHOOK_PATH) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            header("X-Twilio-Signature", signature)
            params.forEach { (k, v) -> param(k, v) }
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_XML) }
            content { string(org.hamcrest.Matchers.containsString("<Message>")) }
            content { string(org.hamcrest.Matchers.containsString("Em breve poderei contar suas colmeias")) }
        }
    }

    @Test
    fun `invalid signature returns 403`() {
        val params = linkedMapOf("From" to "whatsapp:+5511999999999", "Body" to "oi")

        mockMvc.post(WEBHOOK_PATH) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            header("X-Twilio-Signature", "definitely-not-valid")
            params.forEach { (k, v) -> param(k, v) }
        }.andExpect {
            status { isForbidden() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
        }
    }

    @Test
    fun `missing signature returns 403`() {
        val params = linkedMapOf("From" to "whatsapp:+5511999999999", "Body" to "oi")

        mockMvc.post(WEBHOOK_PATH) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            params.forEach { (k, v) -> param(k, v) }
        }.andExpect {
            status { isForbidden() }
        }
    }

    /**
     * Reproduces Twilio's signing algorithm: append each param (sorted by key) as
     * key+value to the URL, then HMAC-SHA1 with the auth token and Base64-encode.
     */
    private fun twilioSignature(url: String, params: Map<String, String>): String {
        val data = StringBuilder(url)
        params.toSortedMap().forEach { (k, v) -> data.append(k).append(v) }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(AUTH_TOKEN.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().toByteArray(Charsets.UTF_8)))
    }

    companion object {
        const val AUTH_TOKEN = "test-auth-token"
        const val PUBLIC_BASE_URL = "https://example.test"
        const val WEBHOOK_PATH = "/v1/whatsapp/webhook"
        const val SIGNED_URL = "$PUBLIC_BASE_URL$WEBHOOK_PATH"
    }
}
