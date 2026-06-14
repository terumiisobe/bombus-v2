package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.outbound.ColmeiaCountPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCountFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `grouped queries are rejected (handled by the breakdown capability)`() {
        val service = service(RecordingCountPort(result = 0))

        assertFailsWith<IllegalArgumentException> {
            service.count(CountColmeiasQuery(userId = OWNER, groupBy = setOf(CountDimension.SPECIES)))
        }
    }

    private fun service(
        port: ColmeiaCountPort,
        statuses: Map<String, Long> = mapOf("perdida" to perdidaId),
    ) = CountColmeiasService(
        countPort = port,
        statusLookupPort = FakeStatusLookupPort(statuses),
        properties = ColmeiaCountProperties(defaultExcludedStatus = "perdida"),
    )

    private class RecordingCountPort(private val result: Long) : ColmeiaCountPort {
        var lastUserId: Long? = null
            private set
        var lastFilter: ColmeiaCountFilter? = null
            private set

        override fun countByOwner(userId: Long, filter: ColmeiaCountFilter): Long {
            lastUserId = userId
            lastFilter = filter
            return result
        }
    }

    private class FakeStatusLookupPort(private val byName: Map<String, Long>) : StatusColmeiaLookupPort {
        override fun findIdByName(name: String): Long? = byName[name]
    }

    private companion object {
        const val OWNER = 42L
    }
}
