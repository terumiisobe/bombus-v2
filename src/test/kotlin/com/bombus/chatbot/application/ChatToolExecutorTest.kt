package com.bombus.chatbot.application

import com.bombus.chatbot.domain.AssistantToolCall
import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.CreateColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.DeletedColmeia
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaUseCase
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.ColmeiaVocabulary
import com.bombus.colmeia.domain.SpeciesCount
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatToolExecutorTest {

    private val objectMapper = jacksonObjectMapper()
    private val count = RecordingCount(ColmeiaCount(total = 0))
    private val listOwned = RecordingList()
    private val create = RecordingCreate()
    private val update = RecordingUpdate()
    private val delete = RecordingDelete()
    private val executor = ChatToolExecutor(
        countColmeias = count,
        vocabularyUseCase = FakeVocabulary,
        listOwnedColmeias = listOwned,
        createColmeia = create,
        updateColmeia = update,
        deleteColmeia = delete,
        objectMapper = objectMapper,
    )

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
    fun `list_colmeias returns compact items without internal id`() {
        count.result = ColmeiaCount(total = 1)
        listOwned.items = listOf(SAMPLE)
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_l",
                name = ChatToolNames.LIST_COLMEIAS,
                argumentsJson = """{"limit":5}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals("list", json.path("mode").asText())
        assertEquals(1, json.path("count").asInt())
        assertEquals(7, json.path("items").path(0).path("code").asInt())
        assertEquals("Jataí", json.path("items").path(0).path("species").asText())
        assertTrue(json.path("items").path(0).path("id").isMissingNode)
        assertEquals(
            ListOwnedColmeiasQuery(userId = 42L, limit = 5),
            listOwned.lastQuery,
        )
    }

    @Test
    fun `list_colmeias returns species count when more than 10 hives`() {
        count.result = ColmeiaCount(
            total = 11,
            perSpecies = listOf(SpeciesCount(1, "JT", "Jataí", 11)),
        )
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_lc",
                name = ChatToolNames.LIST_COLMEIAS,
                argumentsJson = "{}",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals("species_count", json.path("mode").asText())
        assertEquals(11, json.path("total").asInt())
        assertEquals("Jataí", json.path("perSpecies").path(0).path("commonName").asText())
        assertEquals(null, listOwned.lastQuery)
    }

    @Test
    fun `create_colmeia maps args to command`() {
        create.result = SAMPLE
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_c",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"speciesId":1,"statusId":3}""",
            ),
        )

        assertEquals(
            CreateColmeiaCommand(userId = 42L, speciesId = 1L, statusId = 3L),
            create.lastCommand,
        )
        val json = objectMapper.readTree(result.contentJson)
        assertTrue(json.path("colmeia").path("id").isMissingNode)
        assertEquals("Jataí", json.path("colmeia").path("species").asText())
    }

    @Test
    fun `delete_colmeia requires confirmed true then hard deletes`() {
        val denied = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_d0",
                name = ChatToolNames.DELETE_COLMEIA,
                argumentsJson = """{"code":7,"confirmed":false}""",
            ),
        )
        assertEquals("ConfirmationRequired", objectMapper.readTree(denied.contentJson).path("error").asText())

        delete.result = DeletedColmeia(code = 7, speciesCommonName = "Jataí", statusName = "estavel")
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_d",
                name = ChatToolNames.DELETE_COLMEIA,
                argumentsJson = """{"code":7,"confirmed":true}""",
            ),
        )

        assertEquals(DeleteColmeiaCommand(userId = 42L, code = 7), delete.lastCommand)
        val json = objectMapper.readTree(result.contentJson)
        assertTrue(json.path("deleted").asBoolean())
        assertEquals(7, json.path("code").asInt())
    }

    @Test
    fun `definitions expose count list vocabulary and CRUD tools`() {
        val names = executor.definitions().map { it.name }
        assertEquals(
            listOf(
                ChatToolNames.COUNT_COLMEIAS,
                ChatToolNames.LIST_VOCABULARY,
                ChatToolNames.LIST_COLMEIAS,
                ChatToolNames.CREATE_COLMEIA,
                ChatToolNames.UPDATE_COLMEIA,
                ChatToolNames.DELETE_COLMEIA,
            ),
            names,
        )
    }

    private class RecordingCount(var result: ColmeiaCount) : CountColmeiasUseCase {
        var lastQuery: CountColmeiasQuery? = null
            private set

        override fun count(query: CountColmeiasQuery): ColmeiaCount {
            lastQuery = query
            return result
        }
    }

    private class RecordingList : ListOwnedColmeiasUseCase {
        var items: List<ColmeiaSummary> = emptyList()
        var lastQuery: ListOwnedColmeiasQuery? = null
            private set

        override fun list(query: ListOwnedColmeiasQuery): List<ColmeiaSummary> {
            lastQuery = query
            return items
        }
    }

    private class RecordingCreate : CreateColmeiaUseCase {
        lateinit var result: ColmeiaSummary
        var lastCommand: CreateColmeiaCommand? = null
            private set

        override fun create(command: CreateColmeiaCommand): ColmeiaSummary {
            lastCommand = command
            return result
        }
    }

    private class RecordingUpdate : UpdateColmeiaUseCase {
        lateinit var result: ColmeiaSummary
        override fun update(command: UpdateColmeiaCommand): ColmeiaSummary = result
    }

    private class RecordingDelete : DeleteColmeiaUseCase {
        lateinit var result: DeletedColmeia
        var lastCommand: DeleteColmeiaCommand? = null
            private set

        override fun delete(command: DeleteColmeiaCommand): DeletedColmeia {
            lastCommand = command
            return result
        }
    }

    private object FakeVocabulary : ListColmeiaVocabularyUseCase {
        override fun list(): ColmeiaVocabulary = ColmeiaVocabulary(
            species = listOf(SpeciesRef(1, "JT", "Jataí", "Tetragonisca angustula")),
            statuses = listOf(StatusRef(3, "estavel")),
        )
    }

    private companion object {
        val SAMPLE = ColmeiaSummary(
            id = 100L,
            code = 7,
            speciesId = 1L,
            speciesAbbreviation = "JT",
            speciesCommonName = "Jataí",
            meliponarioId = 10L,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            statusId = 3L,
            statusName = "estavel",
        )
    }
}
