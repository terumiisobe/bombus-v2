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
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusCommand
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusUseCase
import com.bombus.colmeia.application.ColmeiaCountProperties
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
import kotlin.test.assertNull
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
        recordColmeiaStatus = update,
        deleteColmeia = delete,
        objectMapper = objectMapper,
        countProperties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida")),
    )

    @Test
    fun `count_colmeias zero total includes status filter name in tool JSON`() {
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_z",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":null,"status":"estável","groupBy":[]}""",
            ),
        )

        assertEquals("call_z", result.toolCallId)
        assertEquals(ChatToolNames.COUNT_COLMEIAS, result.name)
        val json = objectMapper.readTree(result.contentJson)
        assertEquals(0, json.path("total").asLong())
        assertTrue(json.path("speciesId").isMissingNode)
        assertTrue(json.path("statusId").isMissingNode)
        assertTrue(json.path("species").isNull)
        assertEquals("estavel", json.path("status").asText())
        assertTrue(json.path("excludedStatusLabel").isNull)
        assertEquals(
            CountColmeiasQuery(userId = 42L, statusId = 3),
            count.lastQuery,
        )
    }

    @Test
    fun `count_colmeias resolves species common name ignoring case and accent`() {
        count.result = ColmeiaCount(total = 2)
        executor.execute(
            userId = 9L,
            call = AssistantToolCall(
                id = "call_s",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":"jataí"}""",
            ),
        )

        assertEquals(
            CountColmeiasQuery(userId = 9L, speciesId = 1),
            count.lastQuery,
        )
    }

    @Test
    fun `count_colmeias unknown species name returns error and does not count`() {
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_bad_sp",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":"xyz","status":null,"groupBy":[]}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals("unknown_species", json.path("error").asText())
        assertNull(count.lastQuery)
    }

    @Test
    fun `count_colmeias unknown status name returns error and does not count`() {
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_bad_st",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":null,"status":"naoexiste","groupBy":[]}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals("unknown_status", json.path("error").asText())
        assertNull(count.lastQuery)
    }

    @Test
    fun `count_colmeias success payload includes scientific name and excluded status label`() {
        count.result = ColmeiaCount(total = 106)
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_ok",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":"Canudo","status":null,"groupBy":[]}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals(106, json.path("total").asLong())
        assertEquals("Canudo", json.path("species").asText())
        assertEquals("Scaptotrigona depilis", json.path("speciesScientificName").asText())
        assertEquals("CN", json.path("speciesAbbreviation").asText())
        assertEquals("perdida, vendida", json.path("excludedStatusLabel").asText())
        assertTrue(json.path("status").isNull)
        assertTrue(json.path("speciesId").isMissingNode)
        assertEquals(
            CountColmeiasQuery(userId = 42L, speciesId = 2L),
            count.lastQuery,
        )
    }

    @Test
    fun `count_colmeias rejects unknown species with valid names`() {
        val result = executor.execute(
            userId = 9L,
            call = AssistantToolCall(
                id = "call_bad",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":"xyz"}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals("unknown_species", json.path("error").asText())
        assertTrue(json.path("message").asText().contains("Jataí"))
        assertEquals(null, count.lastQuery)
    }

    @Test
    fun `count_colmeias reports ambiguous species when common name collides`() {
        val result = executor.execute(
            userId = 9L,
            call = AssistantToolCall(
                id = "call_amb",
                name = ChatToolNames.COUNT_COLMEIAS,
                argumentsJson = """{"species":"Manduri"}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals("ambiguous_species", json.path("error").asText())
        assertTrue(json.path("message").asText().contains("MD"))
        assertTrue(json.path("message").asText().contains("MT"))
        assertEquals(null, count.lastQuery)
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
    fun `list_vocabulary returns species and status names without ids`() {
        val result = executor.execute(
            userId = 1L,
            call = AssistantToolCall(id = "call_v", name = ChatToolNames.LIST_VOCABULARY, argumentsJson = "{}"),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertTrue(json.path("species").path(0).path("id").isMissingNode)
        assertEquals("Jataí", json.path("species").path(0).path("commonName").asText())
        val statusNames = json.path("statuses").map { it.path("name").asText() }
        assertTrue(statusNames.contains("estavel"))
        assertTrue(json.path("statuses").path(0).path("id").isMissingNode)
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
    fun `create_colmeia maps name args to command ids`() {
        create.result = SAMPLE
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_c",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"species":"Jataí","status":"estavel"}""",
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
    fun `update_colmeia resolves status name and identifies by code`() {
        update.result = SAMPLE.copy(statusId = 3L, statusName = "estavel")
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_u",
                name = ChatToolNames.UPDATE_COLMEIA,
                argumentsJson = """{"code":7,"status":"desenvolvendo"}""",
            ),
        )

        assertEquals(
            RecordColmeiaStatusCommand(userId = 42L, code = 7, statusId = 1L, note = null),
            update.lastCommand,
        )
        val json = objectMapper.readTree(result.contentJson)
        assertEquals(7, json.path("colmeia").path("code").asInt())
        assertTrue(json.path("colmeia").path("id").isMissingNode)
    }

    @Test
    fun `update_colmeia forwards optional note and ignores blank note`() {
        update.result = SAMPLE.copy(statusId = 3L, statusName = "estavel")
        executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_u_note",
                name = ChatToolNames.UPDATE_COLMEIA,
                argumentsJson = """{"code":7,"status":"estavel","note":"ainda fraca"}""",
            ),
        )
        assertEquals(
            RecordColmeiaStatusCommand(userId = 42L, code = 7, statusId = 3L, note = "ainda fraca"),
            update.lastCommand,
        )

        executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_u_blank",
                name = ChatToolNames.UPDATE_COLMEIA,
                argumentsJson = """{"code":7,"status":"estavel","note":null}""",
            ),
        )
        assertEquals(
            RecordColmeiaStatusCommand(userId = 42L, code = 7, statusId = 3L, note = null),
            update.lastCommand,
        )
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
    fun `definitions expose name-based species and status params`() {
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
        val countProps = executor.definitions()
            .first { it.name == ChatToolNames.COUNT_COLMEIAS }
            .parametersJsonSchema["properties"] as Map<*, *>
        assertTrue(countProps.containsKey("species"))
        assertTrue(countProps.containsKey("status"))
        assertTrue(!countProps.containsKey("speciesId"))
        assertTrue(!countProps.containsKey("statusId"))
    }

    @Test
    fun `update_colmeia definition is visita with optional note and no visitedAt`() {
        val updateDef = executor.definitions().first { it.name == ChatToolNames.UPDATE_COLMEIA }
        assertTrue(updateDef.description.contains("Acompanhamento/visita", ignoreCase = true))
        assertTrue(updateDef.description.contains("perdida", ignoreCase = true))
        assertTrue(updateDef.description.contains("vendida", ignoreCase = true))
        val props = updateDef.parametersJsonSchema["properties"] as Map<*, *>
        assertTrue(props.containsKey("code"))
        assertTrue(props.containsKey("status"))
        assertTrue(props.containsKey("note"))
        assertTrue(!props.containsKey("visitedAt"))

        val createDef = executor.definitions().first { it.name == ChatToolNames.CREATE_COLMEIA }
        assertTrue(createDef.description.contains("Cadastro", ignoreCase = true))
        val deleteDef = executor.definitions().first { it.name == ChatToolNames.DELETE_COLMEIA }
        assertTrue(deleteDef.description.contains("Cadastro", ignoreCase = true))
        assertTrue(deleteDef.description.contains("perdida", ignoreCase = true))
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

    private class RecordingUpdate : RecordColmeiaStatusUseCase {
        lateinit var result: ColmeiaSummary
        var lastCommand: RecordColmeiaStatusCommand? = null
            private set

        override fun record(command: RecordColmeiaStatusCommand): ColmeiaSummary {
            lastCommand = command
            return result
        }
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
            species = listOf(
                SpeciesRef(1, "JT", "Jataí", "Tetragonisca angustula"),
                SpeciesRef(2, "CN", "Canudo", "Scaptotrigona depilis"),
                SpeciesRef(6, "MD", "Manduri", "Melipona marginata"),
                SpeciesRef(7, "MT", "Manduri", "Melipona torrida"),
            ),
            statuses = listOf(
                StatusRef(1, "desenvolvendo"),
                StatusRef(3, "estavel"),
                StatusRef(4, "perdida"),
                StatusRef(6, "vendida"),
            ),
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
