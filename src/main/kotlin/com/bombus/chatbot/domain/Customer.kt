package com.bombus.chatbot.domain

/**
 * A known customer reached through WhatsApp.
 *
 * [whatsappUserId] is the id of the usuario_whatsapp link row (carried so later flows
 * such as chat sessions, keyed by that id, do not need to re-query). [userId] is the
 * underlying usuario id. [displayName] may be absent.
 */
data class Customer(
    val whatsappUserId: Long,
    val userId: Long,
    val displayName: String?,
)
