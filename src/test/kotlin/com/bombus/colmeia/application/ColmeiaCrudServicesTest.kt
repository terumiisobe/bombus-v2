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
    private val properties = ColmeiaCountProperties(defaultExcludedStatus = "perdida")

    @Test
    fun `create defaults status to em_desenvolvimento and leaves code startDate null`() {
        val port = FakeOwnedPort().apply { meliponarioIds = listOf(10L) }
        val service = CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup)

        val created = service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L))

        assertNull(created.code)
        assertNull(created.startDate)
        assertEquals(STATUS_EM_DESENVOLVIMENTO, created.statusId)
        assertEquals(10L, created.meliponarioId)
        assertEquals(1, port.inserts.size)
    }

    @Test
    fun `create rejects taken user code`() {
        val port = FakeOwnedPort().apply {
            meliponarioIds = listOf(10L)
            takenCodes += 5
        }
        val service = CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup)

        assertFailsWith<ColmeiaCommandError.CodeTaken> {
            service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L, code = 5))
        }
    }

    @Test
    fun `list excludes perdida unless includeLost`() {
        val port = FakeOwnedPort()
        val service = ListOwnedColmeiasService(port, FakeStatusLookup, properties)

        service.list(ListOwnedColmeiasQuery(userId = 1L))
        assertEquals(STATUS_PERDIDA, port.lastExcludeStatusId)

        service.list(ListOwnedColmeiasQuery(userId = 1L, includeLost = true))
        assertEquals(null, port.lastExcludeStatusId)
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
        val service = UpdateColmeiaService(port, FakeVocabulary, clock)

        val updated = service.update(
            UpdateColmeiaCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL),
        )

        assertEquals(1L, updated.speciesId)
        assertEquals(STATUS_ESTAVEL, updated.statusId)
        assertEquals(1, port.appendedStatuses.size)
    }

    @Test
    fun `update by ambiguous code fails`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE, SAMPLE.copy(id = 101L, meliponarioId = 11L))
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, clock)

        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            service.update(UpdateColmeiaCommand(userId = 1L, code = 7, statusId = STATUS_ESTAVEL))
        }
    }

    private class FakeOwnedPort : OwnedColmeiaPort {
        var meliponarioIds: List<Long> = emptyList()
        var takenCodes: MutableSet<Int> = mutableSetOf()
        var lastExcludeStatusId: Long? = null
        val byId = mutableMapOf<Long, ColmeiaSummary>()
        val byCode = mutableMapOf<Int, List<ColmeiaSummary>>()
        val inserts = mutableListOf<ColmeiaSummary>()
        val appendedStatuses = mutableListOf<Pair<Long, Long>>()
        val deletedIds = mutableListOf<Long>()
        private var seq = 200L

        override fun listMeliponarioIdsByOwner(userId: Long): List<Long> = meliponarioIds

        override fun listByOwner(
            userId: Long,
            excludeStatusId: Long?,
            limit: Int,
            offset: Int,
        ): List<ColmeiaSummary> {
            lastExcludeStatusId = excludeStatusId
            return emptyList()
        }

        override fun findByCodeForOwner(userId: Long, code: Int): List<ColmeiaSummary> =
            byCode[code].orEmpty()

        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            exceptColmeiaId: Long?,
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
                statusName = if (initialStatusId == STATUS_EM_DESENVOLVIMENTO) "em_desenvolvimento" else "other",
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
                    STATUS_EM_DESENVOLVIMENTO -> "em_desenvolvimento"
                    STATUS_ESTAVEL -> "estavel"
                    else -> "other"
                },
            )
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
            StatusRef(STATUS_EM_DESENVOLVIMENTO, "em_desenvolvimento"),
            StatusRef(STATUS_ESTAVEL, "estavel"),
            StatusRef(STATUS_PERDIDA, "perdida"),
        )
    }

    private object FakeStatusLookup : StatusColmeiaLookupPort {
        override fun findIdByName(name: String): Long? = when (name) {
            "perdida" -> STATUS_PERDIDA
            "estavel" -> STATUS_ESTAVEL
            "em_desenvolvimento" -> STATUS_EM_DESENVOLVIMENTO
            else -> null
        }
    }

    private companion object {
        const val STATUS_EM_DESENVOLVIMENTO = 1L
        const val STATUS_ESTAVEL = 3L
        const val STATUS_PERDIDA = 4L

        val SAMPLE = ColmeiaSummary(
            id = 100L,
            code = 7,
            speciesId = 1L,
            speciesAbbreviation = "JT",
            speciesCommonName = "Jataí",
            meliponarioId = 10L,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            statusId = STATUS_EM_DESENVOLVIMENTO,
            statusName = "em_desenvolvimento",
        )
    }
}
