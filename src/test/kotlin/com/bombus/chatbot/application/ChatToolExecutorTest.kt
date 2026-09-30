package com.bombus.chatbot.application

import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaVocabulary
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import com.bombus.chatbot.domain.AssistantToolCall
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatToolExecutorTest {

    private val objectMapper = jacksonObjectMapper()
    private val count = RecordingCount(ColmeiaCount(total = 0))
    private val executor = ChatToolExecutor(count, FakeVocabulary, objectMapper)

    @Test
    fun `count_colmeias zero total includes status filter label in tool JSON`() {
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_z",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"speciesId":null,"statusId":3,"groupBy":[]}""",
            ),
        )

        assertEquals("call_z", result.toolCallId)
        assertEquals(ChatToolNames.COUNT_COLMEIAS, result.name)
        val json = objectMapper.readTree(result.contentJson)
        assertEquals(0, json.path("total").asLong())
        assertEquals(3, json.path("statusId").asLong())
        assertEquals("estavel", json.path("statusLabel").asText())
        assertTrue(json.path("speciesLabel").isNull)
        assertEquals(
            CountColmeiasQuery(userId = 42L, statusId = 3),
            count.lastQuery,
        )
    }

    @Test
    fun `count_colmeias maps groupBy SPECIES`() {
        count.result = ColmeiaCount(total = 2)
        executor.execute(
            userId = 9L,
            call = AssistantToolCall(
                id = "call_g",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"groupBy":["SPECIES"]}""",
            ),
        )

        assertEquals(
            CountColmeiasQuery(userId = 9L, groupBy = setOf(CountDimension.SPECIES)),
            count.lastQuery,
        )
    }

    @Test
    fun `list_vocabulary returns species and status id name lists`() {
        val result = executor.execute(
            userId = 1L,
            call = AssistantToolCall(id = "call_v", name = ChatToolNames.LIST_VOCABULARY, argumentsJson = "{}"),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals(1, json.path("species").path(0).path("id").asLong())
        assertEquals("Jataí", json.path("species").path(0).path("commonName").asText())
        assertEquals(3, json.path("statuses").path(0).path("id").asLong())
        assertEquals("estavel", json.path("statuses").path(0).path("name").asText())
    }

    @Test
    fun `definitions expose both tools`() {
        val names = executor.definitions().map { it.name }
        assertEquals(listOf(ChatToolNames.COUNT_COLMEIAS, ChatToolNames.LIST_VOCABULARY), names)
    }

    private class RecordingCount(var result: ColmeiaCount) : CountColmeiasUseCase {
        var lastQuery: CountColmeiasQuery? = null
            private set

        override fun count(query: CountColmeiasQuery): ColmeiaCount {
            lastQuery = query
            return result
        }
    }

    private object FakeVocabulary : ListColmeiaVocabularyUseCase {
        override fun list(): ColmeiaVocabulary = ColmeiaVocabulary(
            species = listOf(SpeciesRef(1, "JT", "Jataí", "Tetragonisca angustula")),
            statuses = listOf(StatusRef(3, "estavel")),
        )
    }
}
