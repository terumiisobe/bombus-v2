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
import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryQuery
import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryUseCase
import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.DetailColmeiaQuery
import com.bombus.colmeia.application.port.inbound.DetailColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.ResolveColmeiaIdUseCase
import com.bombus.colmeia.application.port.inbound.ResolveColmeiaQuery
import com.bombus.colmeia.application.port.inbound.ResolvedColmeia
import com.bombus.colmeia.domain.ColmeiaDetail
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusCommand
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusUseCase
import com.bombus.colmeia.application.ColmeiaCountProperties
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.ColmeiaVocabulary
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatToolExecutorTest {

    private val objectMapper = jacksonObjectMapper()
    private val count = RecordingCount(ColmeiaCount(total = 0))
    private val sample = RecordingListOwned()
    private val resolve = RecordingResolve()
    private val detail = RecordingDetail()
    private val listHistory = RecordingHistory()
    private val create = RecordingCreate()
    private val update = RecordingUpdate()
    private val delete = RecordingDelete()
    private val executor = ChatToolExecutor(
        countColmeias = count,
        listOwnedColmeias = sample,
        vocabularyUseCase = FakeVocabulary,
        resolveColmeiaId = resolve,
        detailColmeia = detail,
        listColmeiaHistory = listHistory,
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
    fun `detail_colmeia returns code species scientific name status and last visit without an id`() {
        detail.result = ColmeiaDetail(
            code = 7,
            speciesCommonName = "Jataí",
            statusName = "estavel",
            lastVisit = ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-01T12:00:00Z"),
                statusName = "estavel",
                note = "ok",
            ),
        )
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_l",
                name = ChatToolNames.DETAIL_COLMEIA,
                argumentsJson = """{"code":7}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals(7, json.path("code").asInt())
        assertEquals("Jataí", json.path("species").asText())
        assertEquals("Tetragonisca angustula", json.path("speciesScientificName").asText())
        assertEquals("estavel", json.path("status").asText())
        assertEquals("2026-10-01", json.path("lastVisit").path("date").asText())
        assertTrue(json.path("id").isMissingNode)
        assertEquals(DetailColmeiaQuery(userId = 42L, colmeiaId = 100L), detail.lastQuery)
    }

    @Test
    fun `create_colmeia without code requires confirmation`() {
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_c0",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"species":"Jataí","status":"estavel"}""",
            ),
        )
        assertEquals("confirmation_required", objectMapper.readTree(result.contentJson).path("error").asText())
        assertNull(create.lastCommand)
    }

    @Test
    fun `create_colmeia with confirmed null code creates and exposes null code`() {
        create.result = SAMPLE.copy(code = null)
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_c_null",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"species":"Jataí","status":"estavel","confirmed":true}""",
            ),
        )

        assertEquals(
            CreateColmeiaCommand(userId = 42L, speciesId = 1L, statusId = 3L, code = null),
            create.lastCommand,
        )
        val json = objectMapper.readTree(result.contentJson)
        assertTrue(json.path("colmeia").path("code").isNull)
        assertEquals("Jataí", json.path("colmeia").path("species").asText())
        assertEquals("Tetragonisca angustula", json.path("colmeia").path("speciesScientificName").asText())
        assertEquals("estavel", json.path("colmeia").path("status").asText())
    }

    @Test
    fun `create_colmeia with code creates immediately`() {
        create.result = SAMPLE
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_c",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"species":"Jataí","status":"estavel","code":7}""",
            ),
        )

        assertEquals(
            CreateColmeiaCommand(userId = 42L, speciesId = 1L, statusId = 3L, code = 7),
            create.lastCommand,
        )
        val json = objectMapper.readTree(result.contentJson)
        assertTrue(json.path("colmeia").path("id").isMissingNode)
        assertEquals(7, json.path("colmeia").path("code").asInt())
        assertEquals("Jataí", json.path("colmeia").path("species").asText())
        assertEquals("Tetragonisca angustula", json.path("colmeia").path("speciesScientificName").asText())
    }

    @Test
    fun `create_colmeia includes scientific name for mandacaia and tubuna`() {
        create.result = SAMPLE.copy(
            speciesId = 4L,
            speciesAbbreviation = "MQ",
            speciesCommonName = "Mandaçaia",
            code = 41,
        )
        val mandacaia = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_mq",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"species":"Mandaçaia","status":"desenvolvendo","code":41}""",
            ),
        )
        assertEquals(
            "Melipona quadrifasciata",
            objectMapper.readTree(mandacaia.contentJson).path("colmeia").path("speciesScientificName").asText(),
        )

        create.result = SAMPLE.copy(
            speciesId = 8L,
            speciesAbbreviation = "TB",
            speciesCommonName = "Tubuna",
            code = 81,
        )
        val tubuna = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_tb",
                name = ChatToolNames.CREATE_COLMEIA,
                argumentsJson = """{"species":"Tubuna","status":"desenvolvendo","code":81}""",
            ),
        )
        assertEquals(
            "Scaptotrigona bipunctata",
            objectMapper.readTree(tubuna.contentJson).path("colmeia").path("speciesScientificName").asText(),
        )
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
            RecordColmeiaStatusCommand(userId = 42L, colmeiaId = 100L, statusId = 1L, note = null),
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
            RecordColmeiaStatusCommand(userId = 42L, colmeiaId = 100L, statusId = 3L, note = "ainda fraca"),
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
            RecordColmeiaStatusCommand(userId = 42L, colmeiaId = 100L, statusId = 3L, note = null),
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

        assertEquals(DeleteColmeiaCommand(userId = 42L, colmeiaId = 100L), delete.lastCommand)
        val json = objectMapper.readTree(result.contentJson)
        assertTrue(json.path("deleted").asBoolean())
        assertEquals(7, json.path("code").asInt())
    }

    @Test
    fun `update of an inactive hive asks once before recording`() {
        resolve.next = ResolvedColmeia(colmeiaId = 100L, inactive = true, statusName = "vendida")
        val asked = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_ask",
                name = ChatToolNames.UPDATE_COLMEIA,
                argumentsJson = """{"code":12345,"status":"desenvolvendo"}""",
            ),
        )
        val askedJson = objectMapper.readTree(asked.contentJson)
        assertEquals("confirmation_required", askedJson.path("error").asText())
        assertTrue(askedJson.path("message").asText().contains("vendida"))
        assertNull(update.lastCommand)

        update.result = SAMPLE.copy(statusId = 1L, statusName = "desenvolvendo")
        executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_yes",
                name = ChatToolNames.UPDATE_COLMEIA,
                argumentsJson = """{"code":12345,"status":"desenvolvendo","confirmed":true}""",
            ),
        )
        assertEquals(100L, update.lastCommand?.colmeiaId)
        assertEquals(1L, update.lastCommand?.statusId)
    }

    @Test
    fun `list_colmeia_history returns date status and note without source or visitedAt`() {
        listHistory.items = listOf(
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-01T12:00:00Z"),
                statusName = "estavel",
                note = "ok",
            ),
        )
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_hist",
                name = ChatToolNames.LIST_COLMEIA_HISTORY,
                argumentsJson = """{"code":7,"limit":5}""",
            ),
        )

        assertEquals(
            ListColmeiaHistoryQuery(userId = 42L, colmeiaId = 100L, limit = 5),
            listHistory.lastQuery,
        )
        val json = objectMapper.readTree(result.contentJson)
        assertEquals(7, json.path("code").asInt())
        assertEquals(1, json.path("count").asInt())
        val visit = json.path("visits").path(0)
        assertEquals("2026-10-01", visit.path("date").asText())
        assertEquals("2026-10-01 · estavel (ok)", json.path("lines").path(0).asText())
        assertEquals("estavel", visit.path("status").asText())
        assertEquals("ok", visit.path("note").asText())
        assertTrue(visit.path("source").isMissingNode)
        assertTrue(visit.path("visitedAt").isMissingNode)
    }

    @Test
    fun `list_colmeia_history presents last N older first with date-only note shape`() {
        listHistory.items = listOf(
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-13T18:00:00Z"),
                statusName = "estavel",
                note = "entrada limpa",
            ),
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-10T12:00:00Z"),
                statusName = "recuperando",
                note = null,
            ),
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-01T12:00:00Z"),
                statusName = "desenvolvendo",
                note = "cadastro",
            ),
        )
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_hist_order",
                name = ChatToolNames.LIST_COLMEIA_HISTORY,
                argumentsJson = """{"code":7,"limit":10}""",
            ),
        )

        val json = objectMapper.readTree(result.contentJson)
        assertEquals(3, json.path("count").asInt())
        assertEquals("2026-10-01 · desenvolvendo (cadastro)", json.path("lines").path(0).asText())
        assertEquals("2026-10-10 · recuperando", json.path("lines").path(1).asText())
        assertEquals("2026-10-13 · estavel (entrada limpa)", json.path("lines").path(2).asText())
        assertEquals("2026-10-01", json.path("visits").path(0).path("date").asText())
        assertEquals("2026-10-13", json.path("visits").path(2).path("date").asText())
        assertTrue(!json.path("lines").path(2).asText().contains("nota:"))
    }

    @Test
    fun `list_colmeia_history defaults limit and requires code`() {
        listHistory.items = emptyList()
        executor.execute(
            userId = 1L,
            call = AssistantToolCall(
                id = "call_hist_def",
                name = ChatToolNames.LIST_COLMEIA_HISTORY,
                argumentsJson = """{"code":3}""",
            ),
        )
        assertEquals(
            ListColmeiaHistoryQuery(userId = 1L, colmeiaId = 100L, limit = ListColmeiaHistoryQuery.DEFAULT_LIMIT),
            listHistory.lastQuery,
        )

        listHistory.lastQuery = null
        val missing = executor.execute(
            userId = 1L,
            call = AssistantToolCall(
                id = "call_hist_miss",
                name = ChatToolNames.LIST_COLMEIA_HISTORY,
                argumentsJson = """{}""",
            ),
        )
        assertEquals("missing_code", objectMapper.readTree(missing.contentJson).path("error").asText())
        assertNull(listHistory.lastQuery)
    }

    @Test
    fun `sample_colmeias returns bounded items with scientific names and truncated flag`() {
        count.result = ColmeiaCount(total = 3)
        sample.items = listOf(
            SAMPLE.copy(id = 1, code = 12345, speciesId = 1, speciesCommonName = "Jataí", statusId = 6, statusName = "vendida"),
            SAMPLE.copy(id = 2, code = 12345, speciesId = 8, speciesAbbreviation = "TB", speciesCommonName = "Tubuna", statusId = 6, statusName = "vendida"),
            SAMPLE.copy(id = 3, code = 20000, speciesId = 4, speciesAbbreviation = "MQ", speciesCommonName = "Mandaçaia", statusId = 6, statusName = "vendida"),
        )
        val result = executor.execute(
            userId = 42L,
            call = AssistantToolCall(
                id = "call_sample",
                name = ChatToolNames.SAMPLE_COLMEIAS,
                argumentsJson = """{"status":"vendida"}""",
            ),
        )

        assertEquals(
            ListOwnedColmeiasQuery(userId = 42L, statusId = 6L, limit = ListOwnedColmeiasQuery.DEFAULT_LIMIT),
            sample.lastQuery,
        )
        assertEquals(CountColmeiasQuery(userId = 42L, statusId = 6L), count.lastQuery)
        val json = objectMapper.readTree(result.contentJson)
        assertEquals(3, json.path("total").asLong())
        assertEquals(3, json.path("returned").asInt())
        assertEquals(20, json.path("limit").asInt())
        assertFalse(json.path("truncated").asBoolean())
        assertEquals("vendida", json.path("status").asText())
        assertTrue(json.path("excludedStatusLabel").isNull)
        assertEquals(3, json.path("items").size())
        assertEquals(12345, json.path("items").path(0).path("code").asInt())
        assertEquals("Jataí", json.path("items").path(0).path("species").asText())
        assertEquals("Tetragonisca angustula", json.path("items").path(0).path("speciesScientificName").asText())
        assertEquals("vendida", json.path("items").path(0).path("status").asText())
        assertEquals("Tubuna", json.path("items").path(1).path("species").asText())
        assertEquals("Scaptotrigona bipunctata", json.path("items").path(1).path("speciesScientificName").asText())
        assertEquals(20000, json.path("items").path(2).path("code").asInt())
        assertEquals("Mandaçaia", json.path("items").path(2).path("species").asText())
        assertTrue(json.path("items").path(0).path("id").isMissingNode)
    }

    @Test
    fun `sample_colmeias marks truncated when total exceeds returned`() {
        count.result = ColmeiaCount(total = 25)
        sample.items = listOf(SAMPLE, SAMPLE.copy(id = 101, code = 8))
        val result = executor.execute(
            userId = 9L,
            call = AssistantToolCall(
                id = "call_trunc",
                name = ChatToolNames.SAMPLE_COLMEIAS,
                argumentsJson = """{"limit":2}""",
            ),
        )

        assertEquals(ListOwnedColmeiasQuery(userId = 9L, limit = 2), sample.lastQuery)
        val json = objectMapper.readTree(result.contentJson)
        assertEquals(25, json.path("total").asLong())
        assertEquals(2, json.path("returned").asInt())
        assertTrue(json.path("truncated").asBoolean())
        assertEquals("perdida, vendida", json.path("excludedStatusLabel").asText())
    }

    @Test
    fun `definitions expose name-based species and status params`() {
        val names = executor.definitions().map { it.name }
        assertEquals(
            listOf(
                ChatToolNames.COUNT_COLMEIAS,
                ChatToolNames.SAMPLE_COLMEIAS,
                ChatToolNames.LIST_VOCABULARY,
                ChatToolNames.DETAIL_COLMEIA,
                ChatToolNames.LIST_COLMEIA_HISTORY,
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

        val historyDef = executor.definitions().first { it.name == ChatToolNames.LIST_COLMEIA_HISTORY }
        assertTrue(historyDef.description.contains("histórico", ignoreCase = true))
        assertTrue(historyDef.description.contains("older first", ignoreCase = true))
        val historyProps = historyDef.parametersJsonSchema["properties"] as Map<*, *>
        assertTrue(historyProps.containsKey("code"))
        assertTrue(historyProps.containsKey("limit"))
        assertTrue(!historyProps.containsKey("source"))
        assertTrue(!historyProps.containsKey("visitedAt"))
    }

    @Test
    fun `update_colmeia definition is visita with optional note and no visitedAt`() {
        val updateDef = executor.definitions().first { it.name == ChatToolNames.UPDATE_COLMEIA }
        assertTrue(updateDef.description.contains("soft exit", ignoreCase = true))
        assertTrue(updateDef.description.contains("never delete_colmeia", ignoreCase = true))
        assertTrue(updateDef.description.contains("perdida or vendida", ignoreCase = true))
        assertTrue(updateDef.description.contains("sole hive", ignoreCase = true))
        assertTrue(updateDef.description.contains("without currentStatus", ignoreCase = true))
        assertTrue(!updateDef.description.contains("Do not prefer this tool over delete"))
        assertTrue(!updateDef.description.contains("same-status confirmations"))
        assertTrue(!updateDef.description.contains("listar"))
        val props = updateDef.parametersJsonSchema["properties"] as Map<*, *>
        assertTrue(props.containsKey("code"))
        assertTrue(props.containsKey("status"))
        assertTrue(props.containsKey("note"))
        assertTrue(props.containsKey("confirmed"))
        assertTrue(!props.containsKey("visitedAt"))
        val currentStatus = props["currentStatus"] as Map<*, *>
        assertTrue(currentStatus["description"].toString().contains("Omit for a sole hive"))

        val deleteDef = executor.definitions().first { it.name == ChatToolNames.DELETE_COLMEIA }
        assertTrue(deleteDef.description.contains("excluir", ignoreCase = true))
        assertTrue(deleteDef.description.contains("Never use this tool to mark perdida", ignoreCase = true))
        assertTrue(deleteDef.description.contains("update_colmeia", ignoreCase = true))
        assertTrue(deleteDef.description.contains("same delete flow", ignoreCase = true))
        assertTrue(deleteDef.description.contains("resolved hive id", ignoreCase = true))
        assertTrue(deleteDef.description.contains("first confirmation", ignoreCase = true))
        assertTrue(deleteDef.description.contains("sole hive", ignoreCase = true))
        assertTrue(deleteDef.description.contains("Do not redirect excluir", ignoreCase = true))
        assertTrue(!deleteDef.description.contains("naming that status", ignoreCase = true))
        assertTrue(!deleteDef.description.contains("registry mistakes", ignoreCase = true))

        val detailDef = executor.definitions().first { it.name == ChatToolNames.DETAIL_COLMEIA }
        assertTrue(detailDef.description.contains("sole hive", ignoreCase = true))
        assertTrue(detailDef.description.contains("without status", ignoreCase = true))
        assertTrue(detailDef.description.contains("do not invent not-found", ignoreCase = true))
        val detailStatus = (detailDef.parametersJsonSchema["properties"] as Map<*, *>)["status"] as Map<*, *>
        assertTrue(detailStatus["description"].toString().contains("Omit for a sole hive"))

        val countDef = executor.definitions().first { it.name == ChatToolNames.COUNT_COLMEIAS }
        assertTrue(countDef.description.contains("not a list of codes", ignoreCase = true))
        assertTrue(countDef.description.contains("Never invent names, hive rows", ignoreCase = true))
    }

    @Test
    fun `create_colmeia definition prefers code and requires confirm for null code`() {
        val createDef = executor.definitions().first { it.name == ChatToolNames.CREATE_COLMEIA }
        assertTrue(createDef.description.contains("Prefer asking for a código") || createDef.description.contains("código"))
        assertTrue(createDef.description.contains("confirmed=true"))
        assertTrue(createDef.description.contains("sem código"))
        assertTrue(createDef.description.contains("never invent", ignoreCase = true))
        val props = createDef.parametersJsonSchema["properties"] as Map<*, *>
        assertTrue(props.containsKey("code"))
        assertTrue(props.containsKey("confirmed"))
        assertTrue(props.containsKey("startDate"))
    }

    private class RecordingCount(var result: ColmeiaCount) : CountColmeiasUseCase {
        var lastQuery: CountColmeiasQuery? = null
            private set

        override fun count(query: CountColmeiasQuery): ColmeiaCount {
            lastQuery = query
            return result
        }
    }

    private class RecordingListOwned : ListOwnedColmeiasUseCase {
        var items: List<ColmeiaSummary> = emptyList()
        var lastQuery: ListOwnedColmeiasQuery? = null
            private set

        override fun list(query: ListOwnedColmeiasQuery): List<ColmeiaSummary> {
            lastQuery = query
            return items
        }
    }

    private class RecordingResolve : ResolveColmeiaIdUseCase {
        var next = ResolvedColmeia(colmeiaId = 100L, inactive = false, statusName = "desenvolvendo")
        override fun resolve(query: ResolveColmeiaQuery): ResolvedColmeia = next
    }

    private class RecordingDetail : DetailColmeiaUseCase {
        var result = ColmeiaDetail(code = 7, speciesCommonName = "Jataí", statusName = "estavel", lastVisit = null)
        var lastQuery: DetailColmeiaQuery? = null
            private set

        override fun detail(query: DetailColmeiaQuery): ColmeiaDetail {
            lastQuery = query
            return result
        }
    }

    private class RecordingHistory : ListColmeiaHistoryUseCase {
        var items: List<ColmeiaStatusHistoryEntry> = emptyList()
        var lastQuery: ListColmeiaHistoryQuery? = null

        override fun list(query: ListColmeiaHistoryQuery): List<ColmeiaStatusHistoryEntry> {
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
                SpeciesRef(4, "MQ", "Mandaçaia", "Melipona quadrifasciata"),
                SpeciesRef(6, "MD", "Manduri", "Melipona marginata"),
                SpeciesRef(7, "MT", "Manduri", "Melipona torrida"),
                SpeciesRef(8, "TB", "Tubuna", "Scaptotrigona bipunctata"),
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
