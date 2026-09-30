package com.bombus.chatbot.application.port.outbound

import com.bombus.chatbot.domain.AgentCompletion
import com.bombus.chatbot.domain.AgentMessage
import com.bombus.chatbot.domain.ToolDefinition

/**
 * Driven (outbound) port for the conversation AI.
 * Performs one model completion given messages and available tools.
 * It never executes tools or invents counts — the application owns the tool loop.
 */
interface ConversationAiPort {

    fun complete(messages: List<AgentMessage>, tools: List<ToolDefinition>): AgentCompletion
}
