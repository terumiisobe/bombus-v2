package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaCommand
import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
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
        val service = CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup, properties)

        val created = service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L))

        assertNull(created.code)
        assertNull(created.startDate)
        assertEquals(STATUS_DESENVOLVENDO, created.statusId)
        assertEquals(10L, created.meliponarioId)
        assertEquals(1, port.inserts.size)
    }

    @Test
    fun `create rejects taken user code`() {
        val port = FakeOwnedPort().apply {
            meliponarioIds = listOf(10L)
            takenCodes += 5
        }
        val service = CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup, properties)

        assertFailsWith<ColmeiaCommandError.CodeTaken> {
            service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L, code = 5))
        }
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
    fun `delete hard-deletes by code`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = DeleteColmeiaService(port)

        val deleted = service.delete(DeleteColmeiaCommand(userId = 1L, code = 7))
        assertEquals(7, deleted.code)
        assertEquals("Jataí", deleted.speciesCommonName)
        assertTrue(port.deletedIds.contains(100L))
    }

    @Test
    fun `update changes status only by code`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, properties, clock)

        val updated = service.update(
            UpdateColmeiaCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL),
        )

        assertEquals(1L, updated.speciesId)
        assertEquals(STATUS_ESTAVEL, updated.statusId)
        assertEquals(1, port.appendedStatuses.size)
        assertTrue(port.clearedCodes.isEmpty())
    }

    @Test
    fun `update to perdida clears code for reuse`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, properties, clock)

        val updated = service.update(
            UpdateColmeiaCommand(userId = 1L, code = 7, statusId = STATUS_PERDIDA),
        )

        assertEquals(STATUS_PERDIDA, updated.statusId)
        assertNull(updated.code)
        assertTrue(port.clearedCodes.contains(100L))
    }

    @Test
    fun `update to vendida clears code for reuse`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE)
            byId[100L] = SAMPLE
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, properties, clock)

        val updated = service.update(
            UpdateColmeiaCommand(userId = 1L, code = 7, statusId = STATUS_VENDIDA),
        )

        assertEquals(STATUS_VENDIDA, updated.statusId)
        assertNull(updated.code)
        assertTrue(port.clearedCodes.contains(100L))
    }

    @Test
    fun `update by ambiguous code fails`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE, SAMPLE.copy(id = 101L, meliponarioId = 11L))
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, properties, clock)

        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            service.update(UpdateColmeiaCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL))
        }
    }

    private class FakeOwnedPort : OwnedColmeiaPort {
        var meliponarioIds: List<Long> = emptyList()
        var takenCodes: MutableSet<Int> = mutableSetOf()
        var lastExcludeStatusIds: Set<Long> = emptySet()
        val byId = mutableMapOf<Long, ColmeiaSummary>()
        val byCode = mutableMapOf<Int, List<ColmeiaSummary>>()
        val inserts = mutableListOf<ColmeiaSummary>()
        val appendedStatuses = mutableListOf<Pair<Long, Long>>()
        val clearedCodes = mutableListOf<Long>()
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

        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            exceptColmeiaId: Long?,
            ignoreStatusIds: Collection<Long>,
        ): Boolean = code in takenCodes

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

        override fun appendStatus(colmeiaId: Long, statusId: Long, recordedAt: Instant): ColmeiaSummary? {
            val current = byId[colmeiaId] ?: return null
            appendedStatuses += colmeiaId to statusId
            val next = current.copy(
                statusId = statusId,
                statusName = when (statusId) {
                    STATUS_PERDIDA -> "perdida"
                    STATUS_VENDIDA -> "vendida"
                    STATUS_DESENVOLVENDO -> "desenvolvendo"
                    STATUS_ESTAVEL -> "estavel"
                    else -> "other"
                },
            )
            byId[colmeiaId] = next
            return next
        }

        override fun clearCode(colmeiaId: Long): ColmeiaSummary? {
            val current = byId[colmeiaId] ?: return null
            clearedCodes += colmeiaId
            val next = current.copy(code = null)
            byId[colmeiaId] = next
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
