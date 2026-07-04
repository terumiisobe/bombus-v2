package com.bombus.chatbot.domain

// [count] is the already-computed, trusted number; a reply must restate it verbatim and
// never let the model invent it.
data class ReplyRequest(
    val count: Long,
    val speciesLabel: String? = null,
    val statusLabel: String? = null,
    val language: String = "pt-BR",
)
