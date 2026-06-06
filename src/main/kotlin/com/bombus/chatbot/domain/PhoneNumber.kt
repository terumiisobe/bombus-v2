package com.bombus.chatbot.domain

/**
 * A customer's phone number in canonical E.164 form (leading '+', then digits).
 *
 * Channel-specific artefacts such as Twilio's "whatsapp:" prefix are NOT accepted here;
 * stripping them is the responsibility of the inbound adapter. The domain only knows
 * canonical phone numbers.
 */
data class PhoneNumber(val value: String) {

    init {
        require(value.isNotBlank()) { "Phone number must not be blank" }
        require(E164.matches(value)) { "Phone number must be E.164 with a leading '+': '$value'" }
    }

    companion object {
        private val E164 = Regex("^\\+[1-9]\\d{6,14}$")
    }
}
