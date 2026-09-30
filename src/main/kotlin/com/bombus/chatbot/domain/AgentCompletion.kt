package com.bombus.chatbot.domain

/**
 * Result of one model completion in a tool-calling turn.
 */
sealed interface AgentCompletion {
    data class FinalReply(val text: String) : AgentCompletion
    data class ToolCalls(val calls: List<AssistantToolCall>) : AgentCompletion
    data class Failed(val reason: String) : AgentCompletion
}
