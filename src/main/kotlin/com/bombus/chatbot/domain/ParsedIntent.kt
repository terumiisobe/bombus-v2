package com.bombus.chatbot.domain

sealed interface ParsedIntent {

    data class Count(
        val speciesId: Long? = null,
        val statusId: Long? = null,
    ) : ParsedIntent

    data object Help : ParsedIntent

    data object Unknown : ParsedIntent
}
