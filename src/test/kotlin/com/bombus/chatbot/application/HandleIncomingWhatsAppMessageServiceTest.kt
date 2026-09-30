package com.bombus.chatbot.application

import com.bombus.chatbot.application.port.inbound.IncomingMessage
import com.bombus.chatbot.application.port.inbound.ResolveCustomerUseCase
import com.bombus.chatbot.application.port.outbound.ChatSessionPort
import com.bombus.chatbot.application.port.outbound.ConversationAiPort
import com.bombus.chatbot.application.port.outbound.SaveSessionCommand
import com.bombus.chatbot.application.port.outbound.StoredSession
import com.bombus.chatbot.domain.AgentCompletion
import com.bombus.chatbot.domain.AgentMessage
import com.bombus.chatbot.domain.AssistantToolCall
import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.ConversationRole
import com.bombus.chatbot.domain.ConversationTurn
import com.bombus.chatbot.domain.Customer
import com.bombus.chatbot.domain.CustomerResolution
import com.bombus.chatbot.domain.ToolDefinition
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.CreateColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaUseCase
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.ColmeiaVocabulary
import com.bombus.colmeia.domain.SpeciesCount
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    private val objectMapper = jacksonObjectMapper()
    private val agentProperties = ChatbotAgentProperties(
        maxToolRounds = 2,
        fallbackReply = FALLBACK,
    )

    @Test
    fun `not-linked sender returns the support message and persists no session`() {
        val session = RecordingSessionPort()
        val service = service(resolution = CustomerResolution.NotLinked, session = session)

        val reply = service.handle(IncomingMessage(PHONE, "oi"))

        assertTrue(reply.contains("entre em contato com o suporte"))
        assertNull(session.saved)
    }

    @Test
    fun `plain count tool then final reply uses the tool total`() {
        val count = RecordingCount(result = ColmeiaCount(total = 7))
        val ai = FakeConversationAi(
            listOf(
                AgentCompletion.ToolCalls(
                    listOf(
                        AssistantToolCall(
                            id = "call_1",
                            name = ChatToolNames.COUNT_COLMEIAS,
                            argumentsJson = """{"speciesId":null,"statusId":null,"groupBy":[]}""",
                        ),
                    ),
                ),
                AgentCompletion.FinalReply("Você tem 7 colmeias."),
            ),
        )
        val service = service(ai = ai, count = count)

        val reply = service.handle(IncomingMessage(PHONE, "quantas colmeias eu tenho?"))

        assertEquals("Você tem 7 colmeias.", reply)
        assertEquals(CountColmeiasQuery(userId = USER_ID), count.lastQuery)
        assertEquals(2, ai.completeCalls)
        val toolMsg = ai.lastMessages.filterIsInstance<AgentMessage.Tool>().single()
        assertTrue(toolMsg.result.contentJson.contains("\"total\":7"))
    }

    @Test
    fun `groupBy SPECIES with status filter returns breakdown then final reply`() {
        val count = RecordingCount(
            result = ColmeiaCount(
                total = 0,
                perSpecies = listOf(
                    SpeciesCount(speciesId = 1, abbreviation = "JT", commonName = "Jataí", count = 0),
                ),
            ),
        )
        val ai = FakeConversationAi(
            listOf(
                AgentCompletion.ToolCalls(
                    listOf(
                        AssistantToolCall(
                            id = "call_gb",
                            name = ChatToolNames.COUNT_COLMEIAS,
                            argumentsJson = """{"speciesId":null,"statusId":3,"groupBy":["SPECIES"]}""",
                        ),
                    ),
                ),
                AgentCompletion.FinalReply("Você tem 0 colmeias estáveis por espécie."),
            ),
        )
        val service = service(ai = ai, count = count)

        val reply = service.handle(IncomingMessage(PHONE, "quantas estaveis por especie"))

        assertEquals("Você tem 0 colmeias estáveis por espécie.", reply)
        assertEquals(
            CountColmeiasQuery(
                userId = USER_ID,
                statusId = 3,
                groupBy = setOf(CountDimension.SPECIES),
            ),
            count.lastQuery,
        )
        val toolJson = ai.lastMessages.filterIsInstance<AgentMessage.Tool>().single().result.contentJson
        assertTrue(toolJson.contains("\"statusLabel\":\"estavel\""))
        assertTrue(toolJson.contains("perSpecies"))
    }

    @Test
    fun `model final reply with no tools answers help`() {
        val count = RecordingCount()
        val ai = FakeConversationAi(
            listOf(AgentCompletion.FinalReply("Posso contar suas colmeias por espécie ou status.")),
        )
        val service = service(ai = ai, count = count)

        val reply = service.handle(IncomingMessage(PHONE, "o que você faz?"))

        assertEquals("Posso contar suas colmeias por espécie ou status.", reply)
        assertNull(count.lastQuery)
        assertEquals(1, ai.completeCalls)
    }

    @Test
    fun `max rounds exceeded returns the fallback reply`() {
        val ai = FakeConversationAi(
            listOf(
                AgentCompletion.ToolCalls(
                    listOf(
                        AssistantToolCall(
                            id = "c1",
                            name = ChatToolNames.COUNT_COLMEIAS,
                            argumentsJson = "{}",
                        ),
                    ),
                ),
                AgentCompletion.ToolCalls(
                    listOf(
                        AssistantToolCall(
                            id = "c2",
                            name = ChatToolNames.COUNT_COLMEIAS,
                            argumentsJson = "{}",
                        ),
                    ),
                ),
            ),
        )
        val service = service(ai = ai)

        val reply = service.handle(IncomingMessage(PHONE, "quantas?"))

        assertEquals(FALLBACK, reply)
        assertEquals(2, ai.completeCalls)
    }

    @Test
    fun `AI failure returns the fallback reply`() {
        val ai = FakeConversationAi(listOf(AgentCompletion.Failed("timeout")))
        val service = service(ai = ai)

        val reply = service.handle(IncomingMessage(PHONE, "oi"))

        assertEquals(FALLBACK, reply)
    }

    @Test
    fun `expired session is treated as a fresh conversation`() {
        val expired = StoredSession(
            context = ConversationContext(listOf(ConversationTurn(ConversationRole.USER, "antigo"))),
            expiresAt = now.minusSeconds(1),
        )
        val ai = FakeConversationAi(listOf(AgentCompletion.FinalReply("ajuda")))
        val service = service(ai = ai, session = RecordingSessionPort(stored = expired))

        service.handle(IncomingMessage(PHONE, "oi"))

        assertEquals(listOf("oi"), ai.lastMessages.filterIsInstance<AgentMessage.User>().map { it.text })
    }

    @Test
    fun `a live session context is forwarded as prior agent messages`() {
        val live = StoredSession(
            context = ConversationContext(listOf(ConversationTurn(ConversationRole.USER, "quantas jataí?"))),
            expiresAt = now.plusSeconds(60),
        )
        val ai = FakeConversationAi(listOf(AgentCompletion.FinalReply("ajuda")))
        val service = service(ai = ai, session = RecordingSessionPort(stored = live))

        service.handle(IncomingMessage(PHONE, "e as estáveis?"))

        assertEquals(
            listOf("quantas jataí?", "e as estáveis?"),
            ai.lastMessages.filterIsInstance<AgentMessage.User>().map { it.text },
        )
    }

    @Test
    fun `persisted context appends the turns, stays bounded, and slides the expiry`() {
        val priorTurns = (1..5).map { ConversationTurn(ConversationRole.USER, "t$it") }
        val session = RecordingSessionPort(
            stored = StoredSession(ConversationContext(priorTurns), expiresAt = now.plusSeconds(60)),
        )
        val ai = FakeConversationAi(listOf(AgentCompletion.FinalReply("ajuda")))
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
        ai: ConversationAiPort = FakeConversationAi(listOf(AgentCompletion.FinalReply("ok"))),
        count: CountColmeiasUseCase = RecordingCount(),
    ): HandleIncomingWhatsAppMessageService {
        val toolExecutor = ChatToolExecutor(
            countColmeias = count,
            vocabularyUseCase = FakeVocabulary,
            listOwnedColmeias = NoopListOwned,
            createColmeia = NoopCreate,
            updateColmeia = NoopUpdate,
            softDeleteColmeia = NoopSoftDelete,
            objectMapper = objectMapper,
        )
        return HandleIncomingWhatsAppMessageService(
            resolveCustomer = FakeResolveCustomer(resolution),
            chatSessionPort = session,
            conversationAi = ai,
            toolExecutor = toolExecutor,
            sessionProperties = ChatSessionProperties(ttl = Duration.ofMinutes(15), maxContextMessages = 5),
            agentProperties = agentProperties,
            clock = clock,
        )
    }

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
        private val scripted: List<AgentCompletion>,
    ) : ConversationAiPort {
        var completeCalls = 0
            private set
        var lastMessages: List<AgentMessage> = emptyList()
            private set

        override fun complete(messages: List<AgentMessage>, tools: List<ToolDefinition>): AgentCompletion {
            lastMessages = messages.toList()
            val index = completeCalls
            completeCalls++
            return scripted.getOrElse(index) { AgentCompletion.Failed("no_scripted_completion") }
        }
    }

    private object FakeVocabulary : ListColmeiaVocabularyUseCase {
        override fun list(): ColmeiaVocabulary = ColmeiaVocabulary(
            species = listOf(SpeciesRef(id = 1, abbreviation = "JT", commonName = "Jataí", scientificName = "Tetragonisca angustula")),
            statuses = listOf(StatusRef(id = 3, name = "estavel")),
        )
    }

    private object NoopListOwned : ListOwnedColmeiasUseCase {
        override fun list(query: ListOwnedColmeiasQuery): List<ColmeiaSummary> = emptyList()
    }

    private object NoopCreate : CreateColmeiaUseCase {
        override fun create(command: CreateColmeiaCommand): ColmeiaSummary =
            error("create not used in this test")
    }

    private object NoopUpdate : UpdateColmeiaUseCase {
        override fun update(command: UpdateColmeiaCommand): ColmeiaSummary =
            error("update not used in this test")
    }

    private object NoopSoftDelete : SoftDeleteColmeiaUseCase {
        override fun softDelete(command: SoftDeleteColmeiaCommand): ColmeiaSummary =
            error("soft-delete not used in this test")
    }

    private companion object {
        const val PHONE = "+5511999999999"
        const val WHATSAPP_USER_ID = 7L
        const val USER_ID = 42L
        const val FALLBACK = "fallback-capability-blurb"
    }
}
