package com.bombus.chatbot.domain

/**
 * Provider-agnostic chat messages for a tool-calling turn.
 * System prompts stay in the AI adapter; the application seeds history + user + tool results.
 */
sealed interface AgentMessage {
    data class User(val text: String) : AgentMessage
    data class Assistant(
        val text: String? = null,
        val toolCalls: List<AssistantToolCall> = emptyList(),
    ) : AgentMessage
    data class Tool(val result: ToolResultMessage) : AgentMessage
}

data class AssistantToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class ToolResultMessage(
    val toolCallId: String,
    val name: String,
    val contentJson: String,
)
