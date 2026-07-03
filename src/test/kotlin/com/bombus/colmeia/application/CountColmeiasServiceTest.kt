package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.outbound.ColmeiaCountPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCountFilter
import com.bombus.colmeia.domain.SpeciesCount
import com.bombus.colmeia.domain.StatusCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountColmeiasServiceTest {

    private val perdidaId = 4L

    @Test
    fun `plain count excludes the default status and keeps sem status`() {
        val port = RecordingCountPort(result = 3)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER))

        assertEquals(3, result.total)
        assertEquals(OWNER, port.lastUserId)
        assertEquals(
            ColmeiaCountFilter(speciesId = null, includeStatusId = null, excludeStatusId = perdidaId),
            port.lastFilter,
        )
    }

    @Test
    fun `species-only count still excludes the default status`() {
        val port = RecordingCountPort(result = 2)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER, speciesId = 1))

        assertEquals(2, result.total)
        assertEquals(
            ColmeiaCountFilter(speciesId = 1, includeStatusId = null, excludeStatusId = perdidaId),
            port.lastFilter,
        )
    }

    @Test
    fun `explicit status returns exactly that status and overrides the default exclusion`() {
        val port = RecordingCountPort(result = 5)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER, statusId = 3))

        assertEquals(5, result.total)
        assertEquals(
            ColmeiaCountFilter(speciesId = null, includeStatusId = 3, excludeStatusId = null),
            port.lastFilter,
        )
    }

    @Test
    fun `explicit perdida status is returnable`() {
        val port = RecordingCountPort(result = 2)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER, statusId = perdidaId))

        assertEquals(2, result.total)
        assertEquals(perdidaId, port.lastFilter?.includeStatusId)
        assertNull(port.lastFilter?.excludeStatusId)
    }

    @Test
    fun `explicit status combines with a species filter`() {
        val port = RecordingCountPort(result = 1)
        val service = service(port)

        service.count(CountColmeiasQuery(userId = OWNER, speciesId = 1, statusId = 3))

        assertEquals(
            ColmeiaCountFilter(speciesId = 1, includeStatusId = 3, excludeStatusId = null),
            port.lastFilter,
        )
    }

    @Test
    fun `unknown id yields zero rather than an error`() {
        val port = RecordingCountPort(result = 0)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER, statusId = 999))

        assertEquals(0, result.total)
    }

    @Test
    fun `no default exclusion is applied when the excluded status name is unknown`() {
        val port = RecordingCountPort(result = 7)
        val service = service(port, statuses = emptyMap())

        service.count(CountColmeiasQuery(userId = OWNER))

        assertNull(port.lastFilter?.excludeStatusId)
    }

    @Test
    fun `per-species breakdown returns all groups without the default exclusion`() {
        val species = listOf(
            SpeciesCount(speciesId = 1, abbreviation = "JT", commonName = "Jataí", count = 3),
            SpeciesCount(speciesId = 2, abbreviation = "EM", commonName = "Mirim emerina", count = 2),
        )
        val port = RecordingCountPort(perSpecies = species)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER, groupBy = setOf(CountDimension.SPECIES)))

        assertEquals(species, result.perSpecies)
        assertNull(result.perStatus)
        assertEquals(5, result.total)
        assertNull(port.lastFilter?.includeStatusId)
        assertNull(port.lastFilter?.excludeStatusId)
    }

    @Test
    fun `per-status breakdown includes perdida and the sem status group`() {
        val statuses = listOf(
            StatusCount(statusId = 1, statusName = "desenvolvendo", count = 1),
            StatusCount(statusId = 3, statusName = "estavel", count = 1),
            StatusCount(statusId = perdidaId, statusName = "perdida", count = 2),
            StatusCount(statusId = null, statusName = null, count = 1),
        )
        val port = RecordingCountPort(perStatus = statuses)
        val service = service(port)

        val result = service.count(CountColmeiasQuery(userId = OWNER, groupBy = setOf(CountDimension.STATUS)))

        assertEquals(statuses, result.perStatus)
        assertNull(result.perSpecies)
        assertEquals(5, result.total)
        assertNull(port.lastFilter?.excludeStatusId)
    }

    @Test
    fun `breakdown by both dimensions populates both lists`() {
        val species = listOf(SpeciesCount(speciesId = 1, abbreviation = "JT", commonName = "Jataí", count = 4))
        val statuses = listOf(StatusCount(statusId = 3, statusName = "estavel", count = 4))
        val port = RecordingCountPort(perSpecies = species, perStatus = statuses)
        val service = service(port)

        val result = service.count(
            CountColmeiasQuery(userId = OWNER, groupBy = setOf(CountDimension.SPECIES, CountDimension.STATUS)),
        )

        assertEquals(species, result.perSpecies)
        assertEquals(statuses, result.perStatus)
        assertEquals(4, result.total)
    }

    @Test
    fun `breakdown narrows by an explicit species filter without default exclusion`() {
        val port = RecordingCountPort(perStatus = emptyList())
        val service = service(port)

        service.count(
            CountColmeiasQuery(userId = OWNER, speciesId = 1, groupBy = setOf(CountDimension.STATUS)),
        )

        assertEquals(
            ColmeiaCountFilter(speciesId = 1, includeStatusId = null, excludeStatusId = null),
            port.lastFilter,
        )
    }

    private fun service(
        port: ColmeiaCountPort,
        statuses: Map<String, Long> = mapOf("perdida" to perdidaId),
    ) = CountColmeiasService(
        countPort = port,
        statusLookupPort = FakeStatusLookupPort(statuses),
        properties = ColmeiaCountProperties(defaultExcludedStatus = "perdida"),
    )

    private class RecordingCountPort(
        private val result: Long = 0,
        private val perSpecies: List<SpeciesCount> = emptyList(),
        private val perStatus: List<StatusCount> = emptyList(),
    ) : ColmeiaCountPort {
        var lastUserId: Long? = null
            private set
        var lastFilter: ColmeiaCountFilter? = null
            private set

        override fun countByOwner(userId: Long, filter: ColmeiaCountFilter): Long {
            record(userId, filter)
            return result
        }

        override fun breakdownBySpecies(userId: Long, filter: ColmeiaCountFilter): List<SpeciesCount> {
            record(userId, filter)
            return perSpecies
        }

        override fun breakdownByStatus(userId: Long, filter: ColmeiaCountFilter): List<StatusCount> {
            record(userId, filter)
            return perStatus
        }

        private fun record(userId: Long, filter: ColmeiaCountFilter) {
            lastUserId = userId
            lastFilter = filter
        }
    }

    private class FakeStatusLookupPort(private val byName: Map<String, Long>) : StatusColmeiaLookupPort {
        override fun findIdByName(name: String): Long? = byName[name]
    }

    private companion object {
        const val OWNER = 42L
    }
}
