package com.bombus.chatbot.domain

data class ConversationContext(
    val recentTurns: List<ConversationTurn> = emptyList(),
)

data class ConversationTurn(
    val role: ConversationRole,
    val text: String,
)

enum class ConversationRole { USER, ASSISTANT }
