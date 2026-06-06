package com.bombus.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Twilio integration settings.
 *
 * [publicBaseUrl] is the externally reachable base URL (e.g. the ngrok https URL) that
 * Twilio uses when it signs the webhook request. It must match the URL configured in the
 * Twilio console exactly, otherwise signature validation fails.
 */
@ConfigurationProperties(prefix = "twilio")
data class TwilioProperties(
    val authToken: String,
    val publicBaseUrl: String,
)
