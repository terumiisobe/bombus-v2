package com.bombus.chatbot.application

import com.bombus.chatbot.application.port.inbound.HandleIncomingWhatsAppMessageUseCase
import com.bombus.chatbot.application.port.inbound.IncomingMessage
import com.bombus.chatbot.application.port.inbound.ResolveCustomerUseCase
import com.bombus.chatbot.application.port.outbound.ChatSessionPort
import com.bombus.chatbot.application.port.outbound.ConversationAiPort
import com.bombus.chatbot.application.port.outbound.SaveSessionCommand
import com.bombus.chatbot.domain.AgentCompletion
import com.bombus.chatbot.domain.AgentMessage
import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.ConversationRole
import com.bombus.chatbot.domain.ConversationTurn
import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.CustomerResolution
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * Orchestrates a single WhatsApp turn: resolve customer → load session context →
 * bounded tool-calling loop → persist sliding-window session → reply.
 *
 * Numbers come only from tool execution ([ChatToolExecutor] → [com.bombus.colmeia]
 * use cases). The model phrases the final pt-BR reply from trusted tool JSON.
 */
@Service
@Transactional
class HandleIncomingWhatsAppMessageService(
    private val resolveCustomer: ResolveCustomerUseCase,
    private val chatSessionPort: ChatSessionPort,
    private val conversationAi: ConversationAiPort,
    private val toolExecutor: ChatToolExecutor,
    private val sessionProperties: ChatSessionProperties,
    private val agentProperties: ChatbotAgentProperties,
    private val clock: Clock,
) : HandleIncomingWhatsAppMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(command: IncomingMessage): String =
        when (val resolution = resolveCustomer.resolve(command.phoneNumber)) {
            CustomerResolution.NotLinked -> NOT_LINKED_MESSAGE
            is CustomerResolution.Resolved -> handleResolved(resolution.customer, command.text)
        }

    private fun handleResolved(customer: Customer, text: String): String {
        val now = clock.instant()
        val context = loadContext(customer.whatsappUserId, now)
        val reply = runAgentLoop(customer.userId, context, text)
        persist(customer.whatsappUserId, context, text, reply, now)
        return reply
    }

    private fun runAgentLoop(userId: Long, context: ConversationContext, text: String): String {
        val messages = mutableListOf<AgentMessage>().apply {
            context.recentTurns.forEach { turn ->
                add(
                    when (turn.role) {
                        ConversationRole.USER -> AgentMessage.User(turn.text)
                        ConversationRole.ASSISTANT -> AgentMessage.Assistant(text = turn.text)
                    },
                )
            }
            add(AgentMessage.User(text))
        }
        val tools = toolExecutor.definitions()

        repeat(agentProperties.maxToolRounds) { round ->
            when (val completion = conversationAi.complete(messages, tools)) {
                is AgentCompletion.FinalReply -> {
                    val reply = completion.text.trim()
                    if (reply.isEmpty()) {
                        log.warn("Agent returned blank final reply on round {}", round + 1)
                        return agentProperties.fallbackReply
                    }
                    return reply
                }
                is AgentCompletion.ToolCalls -> {
                    if (completion.calls.isEmpty()) {
                        log.warn("Agent returned empty tool_calls on round {}", round + 1)
                        return agentProperties.fallbackReply
                    }
                    messages.add(AgentMessage.Assistant(toolCalls = completion.calls))
                    completion.calls.forEach { call ->
                        messages.add(AgentMessage.Tool(toolExecutor.execute(userId, call)))
                    }
                }
                is AgentCompletion.Failed -> {
                    log.warn("Agent completion failed on round {}: {}", round + 1, completion.reason)
                    return agentProperties.fallbackReply
                }
            }
        }

        log.warn("Agent exceeded max tool rounds ({})", agentProperties.maxToolRounds)
        return agentProperties.fallbackReply
    }

    // An expired or missing session reads as a fresh conversation (empty context).
    private fun loadContext(whatsappUserId: Long, now: Instant): ConversationContext {
        val stored = chatSessionPort.load(whatsappUserId) ?: return ConversationContext()
        return if (stored.expiresAt.isAfter(now)) stored.context else ConversationContext()
    }

    private fun persist(
        whatsappUserId: Long,
        context: ConversationContext,
        message: String,
        reply: String,
        now: Instant,
    ) {
        val turns = (
            context.recentTurns +
                ConversationTurn(ConversationRole.USER, message) +
                ConversationTurn(ConversationRole.ASSISTANT, reply)
            ).takeLast(sessionProperties.maxContextMessages)
        chatSessionPort.save(
            SaveSessionCommand(
                whatsappUserId = whatsappUserId,
                context = ConversationContext(turns),
                lastMessageAt = now,
                expiresAt = now.plus(sessionProperties.ttl),
            ),
        )
    }

    private companion object {
        const val NOT_LINKED_MESSAGE =
            "Não encontrei uma conta vinculada a este número. Por favor, entre em contato com o suporte."
    }
}
