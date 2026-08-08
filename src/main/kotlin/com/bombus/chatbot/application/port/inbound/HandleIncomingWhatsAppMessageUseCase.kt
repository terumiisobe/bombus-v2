package com.bombus.chatbot.application.port.inbound

/**
 * Driving (inbound) port: handle a single inbound WhatsApp turn end to end and return the
 * reply text. The adapter is responsible for channel concerns (signature, TwiML); the phone
 * number is already stripped of any channel prefix.
 */
interface HandleIncomingWhatsAppMessageUseCase {

    fun handle(command: IncomingMessage): String
}

data class IncomingMessage(
    val phoneNumber: String,
    val text: String,
)
