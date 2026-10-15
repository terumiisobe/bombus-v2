package com.bombus.chatbot.application.port.inbound

/**
 * Driving (inbound) port: handle a single inbound WhatsApp turn end to end and return the
 * reply text. The adapter is responsible for channel concerns (signature, TwiML); the phone
 * number is already stripped of any channel prefix.
 */
interface HandleIncomingWhatsAppMessageUseCase {

    fun handle(command: IncomingMessage): String
}

/**
 * One inbound WhatsApp turn. [text] is the Twilio `Body` (often empty for location pins).
 * [latitude]/[longitude] come from Twilio location form fields when the user shares a pin.
 */
data class IncomingMessage(
    val phoneNumber: String,
    val text: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val label: String? = null,
) {
    /** Text fed to the agent and persisted in session (includes coords when present). */
    fun textForAgent(): String {
        val body = text.trim()
        if (latitude == null || longitude == null) return body
        val locationLine = buildString {
            append("[localização WhatsApp] latitude=$latitude longitude=$longitude")
            label?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" label=$it") }
            address?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" address=$it") }
        }
        return if (body.isEmpty()) locationLine else "$body\n$locationLine"
    }

    val hasLocation: Boolean get() = latitude != null && longitude != null
}
