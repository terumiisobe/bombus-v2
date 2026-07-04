package com.bombus.chatbot.application

import com.bombus.chatbot.application.port.inbound.HandleIncomingWhatsAppMessageUseCase
import com.bombus.chatbot.application.port.inbound.IncomingMessage
import com.bombus.chatbot.application.port.inbound.ResolveCustomerUseCase
import com.bombus.chatbot.application.port.outbound.ChatSessionPort
import com.bombus.chatbot.application.port.outbound.SaveSessionCommand
import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.ConversationRole
import com.bombus.chatbot.domain.ConversationTurn
import com.bombus.chatbot.domain.CountVocabulary
import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.CustomerResolution
import com.bombus.chatbot.domain.ParsedIntent
import com.bombus.chatbot.domain.ReplyRequest
import com.bombus.chatbot.domain.SpeciesTerm
import com.bombus.chatbot.domain.StatusTerm
import com.bombus.chatbot.application.port.outbound.ConversationAiPort
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * Orchestrates a single WhatsApp turn: resolve customer -> load session context -> parse
 * intent -> (count + phrase | help) -> persist a bounded, sliding-window session -> reply.
 *
 * Expiry and context-trimming policy live here (driven by [ChatSessionProperties] and an
 * injected [Clock]) so they are unit-testable without a database.
 */
@Service
@Transactional
class HandleIncomingWhatsAppMessageService(
    private val resolveCustomer: ResolveCustomerUseCase,
    private val chatSessionPort: ChatSessionPort,
    private val vocabularyUseCase: ListColmeiaVocabularyUseCase,
    private val conversationAi: ConversationAiPort,
    private val countColmeias: CountColmeiasUseCase,
    private val properties: ChatSessionProperties,
    private val clock: Clock,
) : HandleIncomingWhatsAppMessageUseCase {

    override fun handle(command: IncomingMessage): String =
        when (val resolution = resolveCustomer.resolve(command.phoneNumber)) {
            CustomerResolution.NotLinked -> NOT_LINKED_MESSAGE
            is CustomerResolution.Resolved -> handleResolved(resolution.customer, command.text)
        }

    private fun handleResolved(customer: Customer, text: String): String {
        val now = clock.instant()
        val context = loadContext(customer.whatsappUserId, now)
        val vocabulary = loadVocabulary()

        val reply = when (val intent = conversationAi.parseIntent(text, context, vocabulary)) {
            is ParsedIntent.Count -> replyForCount(customer.userId, intent, vocabulary)
            ParsedIntent.Help, ParsedIntent.Unknown -> HELP_MESSAGE
        }

        persist(customer.whatsappUserId, context, text, reply, now)
        return reply
    }

    // An expired or missing session reads as a fresh conversation (empty context).
    private fun loadContext(whatsappUserId: Long, now: Instant): ConversationContext {
        val stored = chatSessionPort.load(whatsappUserId) ?: return ConversationContext()
        return if (stored.expiresAt.isAfter(now)) stored.context else ConversationContext()
    }

    private fun replyForCount(userId: Long, intent: ParsedIntent.Count, vocabulary: CountVocabulary): String {
        val count = countColmeias.count(
            CountColmeiasQuery(userId = userId, speciesId = intent.speciesId, statusId = intent.statusId),
        )
        return conversationAi.phraseReply(
            ReplyRequest(
                count = count.total,
                speciesLabel = intent.speciesId?.let { id -> vocabulary.species.firstOrNull { it.id == id }?.commonName },
                statusLabel = intent.statusId?.let { id -> vocabulary.statuses.firstOrNull { it.id == id }?.name },
            ),
        )
    }

    private fun persist(whatsappUserId: Long, context: ConversationContext, message: String, reply: String, now: Instant) {
        val turns = (
            context.recentTurns +
                ConversationTurn(ConversationRole.USER, message) +
                ConversationTurn(ConversationRole.ASSISTANT, reply)
            ).takeLast(properties.maxContextMessages)
        chatSessionPort.save(
            SaveSessionCommand(
                whatsappUserId = whatsappUserId,
                context = ConversationContext(turns),
                lastMessageAt = now,
                expiresAt = now.plus(properties.ttl),
            ),
        )
    }

    private fun loadVocabulary(): CountVocabulary {
        val vocabulary = vocabularyUseCase.list()
        return CountVocabulary(
            species = vocabulary.species.map { SpeciesTerm(it.id, it.abbreviation, it.commonName, it.scientificName) },
            statuses = vocabulary.statuses.map { StatusTerm(it.id, it.name) },
        )
    }

    private companion object {
        const val NOT_LINKED_MESSAGE =
            "Não encontrei uma conta vinculada a este número. Por favor, entre em contato com o suporte."
        const val HELP_MESSAGE =
            "Posso contar suas colmeias. Experimente perguntar: \"quantas colmeias eu tenho?\", " +
                "\"quantas jataí?\" ou \"quantas colmeias estáveis?\"."
    }
}
