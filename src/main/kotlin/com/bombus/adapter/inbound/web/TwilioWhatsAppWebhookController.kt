package com.bombus.adapter.inbound.web

import com.bombus.chatbot.application.port.inbound.ResolveCustomerUseCase
import com.bombus.config.TwilioProperties
import com.twilio.twiml.MessagingResponse
import com.twilio.twiml.messaging.Message
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Receives inbound WhatsApp messages delivered by Twilio. Thin adapter: it verifies the
 * request signature, strips the channel-specific "whatsapp:" prefix, resolves the sender to
 * a customer, and renders the composed reply as TwiML. No business logic lives here.
 */
@RestController
@Tag(name = "WhatsApp", description = "Inbound WhatsApp messages delivered by Twilio")
class TwilioWhatsAppWebhookController(
    private val properties: TwilioProperties,
    private val signatureValidator: TwilioSignatureValidator,
    private val resolveCustomer: ResolveCustomerUseCase,
    private val replyComposer: WhatsAppReplyComposer,
) {

    @Operation(
        summary = "Receive an inbound WhatsApp message from Twilio",
        description = "Validates the X-Twilio-Signature header and returns a TwiML reply.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "TwiML reply"),
        ApiResponse(responseCode = "403", description = "Missing or invalid Twilio signature"),
    )
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
        val query = request.queryString?.let { "?$it" } ?: ""
        val signedUrl = properties.publicBaseUrl.trimEnd('/') + request.requestURI + query
        signatureValidator.verify(signedUrl, params, signature)

        val phoneNumber = params[FROM_PARAM].orEmpty().removePrefix(WHATSAPP_PREFIX)
        val resolution = resolveCustomer.resolve(phoneNumber)
        val replyText = replyComposer.compose(resolution)

        val twiml = MessagingResponse.Builder()
            .message(Message.Builder(replyText).build())
            .build()
            .toXml()

        return ResponseEntity.ok()
            .contentType(MediaType.valueOf(TWIML_CONTENT_TYPE))
            .body(twiml)
    }

    private companion object {
        const val TWIML_CONTENT_TYPE = "text/xml;charset=UTF-8"
        const val FROM_PARAM = "From"
        const val WHATSAPP_PREFIX = "whatsapp:"
    }
}
