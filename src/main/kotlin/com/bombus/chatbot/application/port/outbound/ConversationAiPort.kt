package com.bombus.chatbot.application.port.outbound

import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.CountVocabulary
import com.bombus.chatbot.domain.ParsedIntent
import com.bombus.chatbot.domain.ReplyRequest

/**
 * Driven (outbound) port for the conversation AI: it understands a free-text message and
 * phrases a computed count in natural language. It never produces the count itself — the
 * number handed to [phraseReply] is trusted and must be restated verbatim.
 */
interface ConversationAiPort {

    fun parseIntent(
        message: String,
        context: ConversationContext,
        vocabulary: CountVocabulary,
    ): ParsedIntent

    fun phraseReply(request: ReplyRequest): String
}
