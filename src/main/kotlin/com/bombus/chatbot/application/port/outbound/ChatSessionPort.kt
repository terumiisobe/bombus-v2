package com.bombus.chatbot.application.port.outbound

import com.bombus.chatbot.domain.ConversationContext
import java.time.Instant

/**
 * Driven (outbound) port for persisting the short conversation context of a WhatsApp user.
 * Persistence-only: expiry and context trimming are the use case's policy, so [expiresAt]
 * is surfaced (not enforced here) and the caller decides whether a session is still valid.
 */
interface ChatSessionPort {

    fun load(whatsappUserId: Long): StoredSession?

    fun save(command: SaveSessionCommand)
}

data class StoredSession(
    val context: ConversationContext,
    val expiresAt: Instant,
)

data class SaveSessionCommand(
    val whatsappUserId: Long,
    val context: ConversationContext,
    val lastMessageAt: Instant,
    val expiresAt: Instant,
)
