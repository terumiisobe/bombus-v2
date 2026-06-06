package com.bombus.adapter.inbound.web

import com.twilio.security.RequestValidator
import org.springframework.stereotype.Component

/**
 * Verifies that an inbound request genuinely came from Twilio by checking the
 * X-Twilio-Signature header against the signed URL and form parameters.
 */
@Component
class TwilioSignatureValidator(
    private val requestValidator: RequestValidator,
) {

    fun verify(url: String, params: Map<String, String>, signature: String?) {
        if (signature.isNullOrBlank()) {
            throw InvalidTwilioSignatureException("Missing X-Twilio-Signature header")
        }
        if (!requestValidator.validate(url, params, signature)) {
            throw InvalidTwilioSignatureException("Invalid X-Twilio-Signature")
        }
    }
}
