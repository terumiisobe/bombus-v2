package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaCommand
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
class ColmeiaCrudServicesTest {

    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = ColmeiaCountProperties(defaultExcludedStatus = "perdida")

    @Test
    fun `create assigns next free code default status and startDate`() {
        val port = FakeOwnedPort().apply {
            meliponarioIds = listOf(10L)
            nextCode = 3
        }
        val service = CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup, properties, clock)

        val created = service.create(CreateColmeiaCommand(userId = 1L, speciesId = 1L))

        assertEquals(3, created.code)
        assertEquals(STATUS_ESTAVEL, created.statusId)
        assertEquals(now, created.startDate)
        assertEquals(10L, created.meliponarioId)
        assertEquals(1, port.inserts.size)
    }

    @Test
    fun `create rejects taken user code among non-perdida`() {
        val port = FakeOwnedPort().apply {
            meliponarioIds = listOf(10L)
            takenCodes += 5
        }
        val service = CreateColmeiaService(port, FakeVocabulary, FakeStatusLookup, properties, clock)

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
    fun `softDelete appends perdida and is idempotent when already lost`() {
        val port = FakeOwnedPort().apply {
            byId[100L] = SAMPLE.copy(statusId = STATUS_ESTAVEL, statusName = "estavel")
        }
        val service = SoftDeleteColmeiaService(port, FakeStatusLookup, properties, clock)

        val first = service.softDelete(SoftDeleteColmeiaCommand(userId = 1L, colmeiaId = 100L))
        assertEquals(STATUS_PERDIDA, first.statusId)
        assertEquals(1, port.appendedStatuses.size)

        port.byId[100L] = first
        val second = service.softDelete(SoftDeleteColmeiaCommand(userId = 1L, colmeiaId = 100L))
        assertEquals(STATUS_PERDIDA, second.statusId)
        assertEquals(1, port.appendedStatuses.size)
    }

    @Test
    fun `update changes species and status`() {
        val port = FakeOwnedPort().apply {
            byId[100L] = SAMPLE
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, clock)

        val updated = service.update(
            UpdateColmeiaCommand(userId = 1L, colmeiaId = 100L, speciesId = 2L, statusId = STATUS_DESENVOLVENDO),
        )

        assertEquals(2L, updated.speciesId)
        assertEquals(STATUS_DESENVOLVENDO, updated.statusId)
    }

    @Test
    fun `update by ambiguous code fails`() {
        val port = FakeOwnedPort().apply {
            byCode[7] = listOf(SAMPLE, SAMPLE.copy(id = 101L, meliponarioId = 11L))
        }
        val service = UpdateColmeiaService(port, FakeVocabulary, clock)

        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            service.update(UpdateColmeiaCommand(userId = 1L, code = 7))
        }
    }

    private class FakeOwnedPort : OwnedColmeiaPort {
        var meliponarioIds: List<Long> = emptyList()
        var nextCode: Int = 1
        var takenCodes: MutableSet<Int> = mutableSetOf()
        var lastExcludeStatusId: Long? = null
        val byId = mutableMapOf<Long, ColmeiaSummary>()
        val byCode = mutableMapOf<Int, List<ColmeiaSummary>>()
        val inserts = mutableListOf<ColmeiaSummary>()
        val appendedStatuses = mutableListOf<Pair<Long, Long>>()
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

        override fun findByIdForOwner(userId: Long, colmeiaId: Long): ColmeiaSummary? = byId[colmeiaId]

        override fun findByCodeForOwner(
            userId: Long,
            code: Int,
            meliponarioId: Long?,
        ): List<ColmeiaSummary> {
            val all = byCode[code].orEmpty()
            return if (meliponarioId == null) all else all.filter { it.meliponarioId == meliponarioId }
        }

        override fun nextFreeCode(meliponarioId: Long, excludeStatusId: Long?): Int = nextCode

        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            excludeStatusId: Long?,
            exceptColmeiaId: Long?,
        ): Boolean = code in takenCodes

        override fun insert(
            code: Int,
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
                statusName = if (initialStatusId == STATUS_ESTAVEL) "estavel" else "other",
            )
            inserts += summary
            byId[summary.id] = summary
            return summary
        }

        override fun update(colmeiaId: Long, speciesId: Long?, startDate: Instant?): ColmeiaSummary? {
            val current = byId[colmeiaId] ?: return null
            val next = current.copy(
                speciesId = speciesId ?: current.speciesId,
                startDate = startDate ?: current.startDate,
            )
            byId[colmeiaId] = next
            return next
        }

        override fun appendStatus(colmeiaId: Long, statusId: Long, recordedAt: Instant): ColmeiaSummary? {
            val current = byId[colmeiaId] ?: return null
            appendedStatuses += colmeiaId to statusId
            val next = current.copy(
                statusId = statusId,
                statusName = when (statusId) {
                    STATUS_PERDIDA -> "perdida"
                    STATUS_DESENVOLVENDO -> "desenvolvendo"
                    STATUS_ESTAVEL -> "estavel"
                    else -> "other"
                },
            )
            byId[colmeiaId] = next
            return next
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
        )
    }

    private object FakeStatusLookup : StatusColmeiaLookupPort {
        override fun findIdByName(name: String): Long? = when (name) {
            "perdida" -> STATUS_PERDIDA
            "estavel" -> STATUS_ESTAVEL
            else -> null
        }
    }

    private companion object {
        const val STATUS_DESENVOLVENDO = 1L
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
            statusId = STATUS_ESTAVEL,
            statusName = "estavel",
        )
    }
}
