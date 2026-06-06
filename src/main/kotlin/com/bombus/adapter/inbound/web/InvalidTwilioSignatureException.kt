package com.bombus.adapter.inbound.web

/** Raised when an inbound webhook request fails Twilio's X-Twilio-Signature verification. */
class InvalidTwilioSignatureException(message: String) : RuntimeException(message)
