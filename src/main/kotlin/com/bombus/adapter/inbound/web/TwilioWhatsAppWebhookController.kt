package com.bombus.adapter.inbound.web

import com.bombus.config.TwilioProperties
import com.twilio.twiml.MessagingResponse
import com.twilio.twiml.messaging.Message
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Receives inbound WhatsApp messages delivered by Twilio. Thin adapter: it verifies the
 * request signature, delegates to the responder, and renders the reply as TwiML. No
 * business logic lives here.
 */
@RestController
class TwilioWhatsAppWebhookController(
    private val properties: TwilioProperties,
    private val signatureValidator: TwilioSignatureValidator,
    private val responder: StaticWhatsAppResponder,
) {

    @PostMapping(
        "/v1/whatsapp/webhook",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [TWIML_CONTENT_TYPE],
    )
    fun receive(
        @RequestParam params: Map<String, String>,
        @RequestHeader(value = "X-Twilio-Signature", required = false) signature: String?,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        val signedUrl = properties.publicBaseUrl.trimEnd('/') + request.requestURI
        signatureValidator.verify(signedUrl, params, signature)

        val twiml = MessagingResponse.Builder()
            .message(Message.Builder(responder.reply()).build())
            .build()
            .toXml()

        return ResponseEntity.ok()
            .contentType(MediaType.valueOf(TWIML_CONTENT_TYPE))
            .body(twiml)
    }

    private companion object {
        const val TWIML_CONTENT_TYPE = "text/xml;charset=UTF-8"
    }
}
