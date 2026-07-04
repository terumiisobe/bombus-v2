package com.bombus.chatbot.application

import com.bombus.chatbot.application.port.inbound.IncomingMessage
import com.bombus.chatbot.application.port.inbound.ResolveCustomerUseCase
import com.bombus.chatbot.application.port.outbound.ChatSessionPort
import com.bombus.chatbot.application.port.outbound.ConversationAiPort
import com.bombus.chatbot.application.port.outbound.SaveSessionCommand
import com.bombus.chatbot.application.port.outbound.StoredSession
import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.ConversationRole
import com.bombus.chatbot.domain.ConversationTurn
import com.bombus.chatbot.domain.CountVocabulary
import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.CustomerResolution
import com.bombus.chatbot.domain.ParsedIntent
import com.bombus.chatbot.domain.ReplyRequest
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaVocabulary
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandleIncomingWhatsAppMessageServiceTest {

    private val now = Instant.parse("2026-07-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `not-linked sender returns the support message and persists no session`() {
        val session = RecordingSessionPort()
        val service = service(resolution = CustomerResolution.NotLinked, session = session)

        val reply = service.handle(IncomingMessage(PHONE, "oi"))

        assertTrue(reply.contains("entre em contato com o suporte"))
        assertNull(session.saved)
    }

    @Test
    fun `count with no filter queries without species or status and phrases the total`() {
        val ai = FakeConversationAi(intent = ParsedIntent.Count(), reply = "Você tem 7 colmeias.")
        val count = RecordingCount(result = ColmeiaCount(total = 7))
        val service = service(ai = ai, count = count)

        val reply = service.handle(IncomingMessage(PHONE, "quantas colmeias eu tenho?"))

        assertEquals("Você tem 7 colmeias.", reply)
        assertEquals(CountColmeiasQuery(userId = USER_ID, speciesId = null, statusId = null), count.lastQuery)
        assertEquals(ReplyRequest(count = 7, speciesLabel = null, statusLabel = null), ai.lastReplyRequest)
    }

    @Test
    fun `count by status resolves the status label from the vocabulary`() {
        val ai = FakeConversationAi(intent = ParsedIntent.Count(statusId = 3), reply = "R")
        val count = RecordingCount(result = ColmeiaCount(total = 2))
        val service = service(ai = ai, count = count)

        service.handle(IncomingMessage(PHONE, "quantas estáveis?"))

        assertEquals(3, count.lastQuery?.statusId)
        assertEquals(ReplyRequest(count = 2, speciesLabel = null, statusLabel = "estavel"), ai.lastReplyRequest)
    }

    @Test
    fun `count by species resolves the species label from the vocabulary`() {
        val ai = FakeConversationAi(intent = ParsedIntent.Count(speciesId = 1), reply = "R")
        val count = RecordingCount(result = ColmeiaCount(total = 4))
        val service = service(ai = ai, count = count)

        service.handle(IncomingMessage(PHONE, "quantas jataí?"))

        assertEquals(1, count.lastQuery?.speciesId)
        assertEquals(ReplyRequest(count = 4, speciesLabel = "Jataí", statusLabel = null), ai.lastReplyRequest)
    }

    @Test
    fun `help intent returns the help message and never counts`() {
        val ai = FakeConversationAi(intent = ParsedIntent.Help)
        val count = RecordingCount(result = ColmeiaCount(total = 0))
        val service = service(ai = ai, count = count)

        val reply = service.handle(IncomingMessage(PHONE, "o que você faz?"))

        assertTrue(reply.contains("Posso contar suas colmeias"))
        assertNull(count.lastQuery)
    }

    @Test
    fun `unknown intent returns the help message`() {
        val service = service(ai = FakeConversationAi(intent = ParsedIntent.Unknown))

        val reply = service.handle(IncomingMessage(PHONE, "qual a previsão do tempo?"))

        assertTrue(reply.contains("Posso contar suas colmeias"))
    }

    @Test
    fun `expired session is treated as a fresh conversation`() {
        val expired = StoredSession(
            context = ConversationContext(listOf(ConversationTurn(ConversationRole.USER, "antigo"))),
            expiresAt = now.minusSeconds(1),
        )
        val ai = FakeConversationAi(intent = ParsedIntent.Help)
        val service = service(ai = ai, session = RecordingSessionPort(stored = expired))

        service.handle(IncomingMessage(PHONE, "oi"))

        assertTrue(ai.lastContext!!.recentTurns.isEmpty())
    }

    @Test
    fun `a live session context is passed to intent parsing`() {
        val live = StoredSession(
            context = ConversationContext(listOf(ConversationTurn(ConversationRole.USER, "quantas jataí?"))),
            expiresAt = now.plusSeconds(60),
        )
        val ai = FakeConversationAi(intent = ParsedIntent.Help)
        val service = service(ai = ai, session = RecordingSessionPort(stored = live))

        service.handle(IncomingMessage(PHONE, "e as estáveis?"))

        assertEquals(listOf("quantas jataí?"), ai.lastContext!!.recentTurns.map { it.text })
    }

    @Test
    fun `persisted context appends the turns, stays bounded, and slides the expiry`() {
        val priorTurns = (1..5).map { ConversationTurn(ConversationRole.USER, "t$it") }
        val session = RecordingSessionPort(
            stored = StoredSession(ConversationContext(priorTurns), expiresAt = now.plusSeconds(60)),
        )
        val ai = FakeConversationAi(intent = ParsedIntent.Help, reply = "ajuda")
        val service = service(ai = ai, session = session)

        service.handle(IncomingMessage(PHONE, "oi"))

        val saved = session.saved!!
        assertEquals(WHATSAPP_USER_ID, saved.whatsappUserId)
        assertEquals(5, saved.context.recentTurns.size)
        val last = saved.context.recentTurns.takeLast(2)
        assertEquals(ConversationTurn(ConversationRole.USER, "oi"), last[0])
        assertEquals(ConversationRole.ASSISTANT, last[1].role)
        assertEquals(now, saved.lastMessageAt)
        assertEquals(now.plus(Duration.ofMinutes(15)), saved.expiresAt)
    }

    private fun service(
        resolution: CustomerResolution = CustomerResolution.Resolved(
            Customer(whatsappUserId = WHATSAPP_USER_ID, userId = USER_ID, displayName = "Ana"),
        ),
        session: ChatSessionPort = RecordingSessionPort(),
        ai: ConversationAiPort = FakeConversationAi(intent = ParsedIntent.Help),
        count: CountColmeiasUseCase = RecordingCount(),
    ) = HandleIncomingWhatsAppMessageService(
        resolveCustomer = FakeResolveCustomer(resolution),
        chatSessionPort = session,
        vocabularyUseCase = FakeVocabulary,
        conversationAi = ai,
        countColmeias = count,
        properties = ChatSessionProperties(ttl = Duration.ofMinutes(15), maxContextMessages = 5),
        clock = clock,
    )

    private class FakeResolveCustomer(private val resolution: CustomerResolution) : ResolveCustomerUseCase {
        override fun resolve(phoneNumber: String): CustomerResolution = resolution
    }

    private class RecordingSessionPort(private val stored: StoredSession? = null) : ChatSessionPort {
        var saved: SaveSessionCommand? = null
            private set

        override fun load(whatsappUserId: Long): StoredSession? = stored

        override fun save(command: SaveSessionCommand) {
            saved = command
        }
    }

    private class RecordingCount(private val result: ColmeiaCount = ColmeiaCount(total = 0)) : CountColmeiasUseCase {
        var lastQuery: CountColmeiasQuery? = null
            private set

        override fun count(query: CountColmeiasQuery): ColmeiaCount {
            lastQuery = query
            return result
        }
    }

    private class FakeConversationAi(
        private val intent: ParsedIntent,
        private val reply: String = "R",
    ) : ConversationAiPort {
        var lastContext: ConversationContext? = null
            private set
        var lastReplyRequest: ReplyRequest? = null
            private set

        override fun parseIntent(message: String, context: ConversationContext, vocabulary: CountVocabulary): ParsedIntent {
            lastContext = context
            return intent
        }

        override fun phraseReply(request: ReplyRequest): String {
            lastReplyRequest = request
            return reply
        }
    }

    private object FakeVocabulary : ListColmeiaVocabularyUseCase {
        override fun list(): ColmeiaVocabulary = ColmeiaVocabulary(
            species = listOf(SpeciesRef(id = 1, abbreviation = "JT", commonName = "Jataí", scientificName = "Tetragonisca angustula")),
            statuses = listOf(StatusRef(id = 3, name = "estavel")),
        )
    }

    private companion object {
        const val PHONE = "+5511999999999"
        const val WHATSAPP_USER_ID = 7L
        const val USER_ID = 42L
    }
}
