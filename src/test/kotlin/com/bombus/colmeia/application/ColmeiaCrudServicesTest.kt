package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusCommand
import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColmeiaCrudServicesTest {

    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `create defaults status to desenvolvendo and leaves code startDate null`() {
        val port = FakeOwnedPort().apply { meliponarioIds = listOf(10L) }
        val service = createService(port)

        val created = service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L))

        assertNull(created.code)
        assertNull(created.startDate)
        assertEquals(STATUS_DESENVOLVENDO, created.statusId)
        assertEquals(10L, created.meliponarioId)
        assertEquals(1, port.inserts.size)
    }

    @Test
    fun `create targets the first accessible yard`() {
        val port = FakeOwnedPort().apply { meliponarioIds = listOf(10L, 11L) }
        val service = createService(port)

        val created = service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L))

        assertEquals(10L, created.meliponarioId)
    }

    @Test
    fun `create rejects code held by another active hive`() {
        val port = FakeOwnedPort().apply {
            meliponarioIds = listOf(10L)
            activeCodes += 5
        }
        val service = createService(port)

        assertFailsWith<ColmeiaCommandError.CodeTaken> {
            service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L, code = 5))
        }
    }

    @Test
    fun `create allows code held only by perdida or vendida`() {
        val port = FakeOwnedPort().apply {
            meliponarioIds = listOf(10L)
            releasingCodes += 5
        }
        val service = createService(port)

        val created = service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L, code = 5))

        assertEquals(5, created.code)
    }

    @Test
    fun `list excludes perdida and vendida unless includeLost`() {
        val port = FakeOwnedPort()
        val service = ListOwnedColmeiasService(port, FakeStatusLookup, properties)

        service.list(ListOwnedColmeiasQuery(userId = 1L))
        assertEquals(setOf(STATUS_PERDIDA, STATUS_VENDIDA), port.lastExcludeStatusIds)

        service.list(ListOwnedColmeiasQuery(userId = 1L, includeLost = true))
        assertEquals(emptySet(), port.lastExcludeStatusIds)
    }

    @Test
    fun `delete hard-deletes the active hive by code`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = deleteService(port)

        val deleted = service.delete(DeleteColmeiaCommand(userId = 1L, code = 7))
        assertEquals(7, deleted.code)
        assertEquals("Jataí", deleted.speciesCommonName)
        assertTrue(port.deletedIds.contains(100L))
    }

    @Test
    fun `delete ignores vendida with the same code and deletes only the active hive`() {
        val sold = SAMPLE.copy(id = 99L, statusId = STATUS_VENDIDA, statusName = "vendida")
        val active = SAMPLE.copy(id = 100L)
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(sold, active)
            byId[99L] = sold
            byId[100L] = active
        }
        val service = deleteService(port)

        service.delete(DeleteColmeiaCommand(userId = 1L, code = 7))

        assertEquals(listOf(100L), port.deletedIds)
    }

    @Test
    fun `delete of code that is only vendida is not found`() {
        val sold = SAMPLE.copy(statusId = STATUS_VENDIDA, statusName = "vendida")
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(sold)
            byId[100L] = sold
        }

        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            deleteService(port).delete(DeleteColmeiaCommand(userId = 1L, code = 7))
        }
        assertTrue(port.deletedIds.isEmpty())
    }

    @Test
    fun `record changes status only on the active hive`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = recordService(port)

        val updated = service.record(
            RecordColmeiaStatusCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL),
        )

        assertEquals(1L, updated.speciesId)
        assertEquals(STATUS_ESTAVEL, updated.statusId)
        assertEquals(1, port.appendedStatuses.size)
        assertEquals(7, updated.code)
        assertEquals(1L, port.appendedStatuses.single().recordedByUserId)
        assertEquals(now, port.appendedStatuses.single().recordedAt)
    }

    @Test
    fun `record appends historico when status is unchanged`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = recordService(port)

        val updated = service.record(
            RecordColmeiaStatusCommand(
                userId = 1L,
                code = 7,
                statusId = STATUS_DESENVOLVENDO,
                note = "ainda fraca",
            ),
        )

        assertEquals(STATUS_DESENVOLVENDO, updated.statusId)
        assertEquals(1, port.appendedStatuses.size)
        val append = port.appendedStatuses.single()
        assertEquals(STATUS_DESENVOLVENDO, append.statusId)
        assertEquals(1L, append.recordedByUserId)
        assertEquals(now, append.recordedAt)
        assertEquals("ainda fraca", append.note)
    }

    @Test
    fun `record to perdida keeps code and skips uniqueness check`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
            activeCodes += 7
        }
        val service = recordService(port)

        val updated = service.record(
            RecordColmeiaStatusCommand(userId = 1L, code = 7, statusId = STATUS_PERDIDA),
        )

        assertEquals(STATUS_PERDIDA, updated.statusId)
        assertEquals(7, updated.code)
    }

    @Test
    fun `record to vendida keeps code on the row`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = recordService(port)

        val updated = service.record(
            RecordColmeiaStatusCommand(userId = 1L, code = 7, statusId = STATUS_VENDIDA),
        )

        assertEquals(STATUS_VENDIDA, updated.statusId)
        assertEquals(7, updated.code)
    }

    @Test
    fun `record of code that is only vendida is not found`() {
        val sold = SAMPLE.copy(statusId = STATUS_VENDIDA, statusName = "vendida")
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(sold)
            byId[100L] = sold
        }

        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            recordService(port).record(
                RecordColmeiaStatusCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL),
            )
        }
        assertTrue(port.appendedStatuses.isEmpty())
    }

    @Test
    fun `record touches only the active hive when code was reused after vendida`() {
        val sold = SAMPLE.copy(id = 100L, statusId = STATUS_VENDIDA, statusName = "vendida")
        val active = SAMPLE.copy(id = 101L, statusId = STATUS_DESENVOLVENDO, statusName = "desenvolvendo")
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(sold, active)
            byId[100L] = sold
            byId[101L] = active
        }
        val service = recordService(port)

        val updated = service.record(
            RecordColmeiaStatusCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL),
        )

        assertEquals(101L, updated.id)
        assertEquals(STATUS_ESTAVEL, updated.statusId)
        assertEquals(1, port.appendedStatuses.size)
        assertEquals(101L, port.appendedStatuses.single().colmeiaId)
        assertEquals(STATUS_ESTAVEL, port.appendedStatuses.single().statusId)
        assertEquals(1L, port.appendedStatuses.single().recordedByUserId)
    }

    @Test
    fun `record by ambiguous active codes fails`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE, SAMPLE.copy(id = 101L, meliponarioId = 11L))
        }
        val service = recordService(port)

        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            service.record(RecordColmeiaStatusCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL))
        }
    }

    @Test
    fun `record rejects note longer than 280 characters`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }

        assertFailsWith<IllegalArgumentException> {
            recordService(port).record(
                RecordColmeiaStatusCommand(
                    userId = 1L,
                    code = 7,
                    statusId = STATUS_ESTAVEL,
                    note = "x".repeat(281),
                ),
            )
        }
        assertTrue(port.appendedStatuses.isEmpty())
    }

    private fun codeAvailability(port: FakeOwnedPort) =
        ColmeiaCodeAvailability(port, FakeStatusLookup, properties)

    private fun activeResolver(port: FakeOwnedPort) =
        ColmeiaActiveCodeResolver(port, properties)

    private fun createService(port: FakeOwnedPort) =
        CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup, codeAvailability(port))

    private fun recordService(port: FakeOwnedPort) =
        RecordColmeiaStatusService(
            port,
            FakeVocabulary,
            properties,
            activeResolver(port),
            codeAvailability(port),
            clock,
        )

    private fun deleteService(port: FakeOwnedPort) =
        DeleteColmeiaService(port, activeResolver(port))

    private class FakeOwnedPort : OwnedColmeiaPort {
        var meliponarioIds: List<Long> = emptyList()
        var activeCodes: MutableSet<Int> = mutableSetOf()
        var releasingCodes: MutableSet<Int> = mutableSetOf()
        var lastExcludeStatusIds: Set<Long> = emptySet()
        val byId = mutableMapOf<Long, ColmeiaSummary>()
        val byCode = mutableMapOf<Int, List<ColmeiaSummary>>()
        val inserts = mutableListOf<ColmeiaSummary>()
        val appendedStatuses = mutableListOf<AppendColmeiaStatus>()
        val deletedIds = mutableListOf<Long>()
        private var seq = 200L

        override fun listMeliponarioIdsByOwner(userId: Long): List<Long> = meliponarioIds

        override fun listByOwner(
            userId: Long,
            excludeStatusIds: Collection<Long>,
            limit: Int,
            offset: Int,
        ): List<ColmeiaSummary> {
            lastExcludeStatusIds = excludeStatusIds.toSet()
            return emptyList()
        }

        override fun findByCodeForOwner(userId: Long, code: Int): List<ColmeiaSummary> =
            byCode[code].orEmpty()

        override fun listStatusHistory(
            userId: Long,
            colmeiaId: Long,
            limit: Int,
        ): List<ColmeiaStatusHistoryEntry> = emptyList()

        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            exceptColmeiaId: Long?,
            ignoreStatusIds: Collection<Long>,
        ): Boolean {
            if (code in activeCodes) return true
            if (ignoreStatusIds.isEmpty() && code in releasingCodes) return true
            return false
        }

        override fun insert(
            code: Int?,
            speciesId: Long,
            meliponarioId: Long,
            startDate: Instant?,
            initialStatusId: Long,
        ): ColmeiaSummary {
            val summary = ColmeiaSummary(
                id = ++seq,
                code = code,
                speciesId = speciesId,
                speciesAbbreviation = "JT",
                speciesCommonName = "Jataí",
                meliponarioId = meliponarioId,
                startDate = startDate,
                statusId = initialStatusId,
                statusName = if (initialStatusId == STATUS_DESENVOLVENDO) "desenvolvendo" else "other",
            )
            inserts += summary
            byId[summary.id] = summary
            return summary
        }

        override fun appendStatus(append: AppendColmeiaStatus): ColmeiaSummary? {
            val current = byId[append.colmeiaId] ?: return null
            appendedStatuses += append
            val next = current.copy(
                statusId = append.statusId,
                statusName = when (append.statusId) {
                    STATUS_PERDIDA -> "perdida"
                    STATUS_VENDIDA -> "vendida"
                    STATUS_DESENVOLVENDO -> "desenvolvendo"
                    STATUS_ESTAVEL -> "estavel"
                    else -> "other"
                },
            )
            byId[append.colmeiaId] = next
            return next
        }

        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long): Boolean {
            if (!byId.containsKey(colmeiaId)) return false
            deletedIds += colmeiaId
            byId.remove(colmeiaId)
            return true
        }
    }

    private object FakeVocabulary : ColmeiaVocabularyPort {
        override fun listSpecies(): List<SpeciesRef> = listOf(
            SpeciesRef(1, "JT", "Jataí", "Tetragonisca angustula"),
            SpeciesRef(2, "EM", "Mirim emerina", "Plebeia emerina"),
        )

        override fun listStatuses(): List<StatusRef> = listOf(
            StatusRef(STATUS_DESENVOLVENDO, "desenvolvendo"),
            StatusRef(STATUS_ESTAVEL, "estavel"),
            StatusRef(STATUS_PERDIDA, "perdida"),
            StatusRef(STATUS_VENDIDA, "vendida"),
        )
    }

    private object FakeStatusLookup : StatusColmeiaLookupPort {
        override fun findIdByName(name: String): Long? = when (name) {
            "perdida" -> STATUS_PERDIDA
            "vendida" -> STATUS_VENDIDA
            "estavel" -> STATUS_ESTAVEL
            "desenvolvendo" -> STATUS_DESENVOLVENDO
            else -> null
        }
    }

    private companion object {
        const val STATUS_DESENVOLVENDO = 1L
        const val STATUS_ESTAVEL = 3L
        const val STATUS_PERDIDA = 4L
        const val STATUS_VENDIDA = 6L

        val SAMPLE = ColmeiaSummary(
            id = 100L,
            code = 7,
            speciesId = 1L,
            speciesAbbreviation = "JT",
            speciesCommonName = "Jataí",
            meliponarioId = 10L,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            statusId = STATUS_DESENVOLVENDO,
            statusName = "desenvolvendo",
        )
    }
}
