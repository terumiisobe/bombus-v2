package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ColmeiaCodeAvailabilityTest {

    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `null code is always available`() {
        val port = RecordingPort(taken = true)
        availability(port).assertAvailable(meliponarioId = 1L, code = null)
        assertNull(port.lastCode)
    }

    @Test
    fun `rejects code held by an active hive`() {
        val port = RecordingPort(taken = true)
        assertFailsWith<ColmeiaCommandError.CodeTaken> {
            availability(port).assertAvailable(meliponarioId = 1L, code = 3)
        }
    }

    @Test
    fun `passes soft uniqueness ignore ids for perdida and vendida`() {
        val port = RecordingPort(taken = false)
        availability(port).assertAvailable(meliponarioId = 10L, code = 4, exceptColmeiaId = 99L)

        assertEqualsSet(setOf(4L, 6L), port.lastIgnoreStatusIds)
        assertEqualsLong(99L, port.lastExceptColmeiaId)
        assertEqualsInt(4, port.lastCode)
    }

    private fun availability(port: OwnedColmeiaPort) =
        ColmeiaCodeAvailability(port, FakeStatusLookup, properties)

    private fun assertEqualsSet(expected: Set<Long>, actual: Collection<Long>?) {
        kotlin.test.assertEquals(expected, actual?.toSet())
    }

    private fun assertEqualsLong(expected: Long?, actual: Long?) {
        kotlin.test.assertEquals(expected, actual)
    }

    private fun assertEqualsInt(expected: Int?, actual: Int?) {
        kotlin.test.assertEquals(expected, actual)
    }

    private class RecordingPort(private val taken: Boolean) : OwnedColmeiaPort {
        var lastCode: Int? = null
        var lastExceptColmeiaId: Long? = null
        var lastIgnoreStatusIds: Collection<Long>? = null

        override fun listMeliponarioIdsByOwner(userId: Long) = emptyList<Long>()
        override fun listByOwner(userId: Long, excludeStatusIds: Collection<Long>, limit: Int, offset: Int) =
            emptyList<ColmeiaSummary>()
        override fun findByCodeForOwner(userId: Long, code: Int) = emptyList<ColmeiaSummary>()
        override fun listStatusHistory(userId: Long, colmeiaId: Long, limit: Int) = emptyList<ColmeiaStatusHistoryEntry>()
        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            exceptColmeiaId: Long?,
            ignoreStatusIds: Collection<Long>,
        ): Boolean {
            lastCode = code
            lastExceptColmeiaId = exceptColmeiaId
            lastIgnoreStatusIds = ignoreStatusIds
            return taken
        }
        override fun insert(
            code: Int?,
            speciesId: Long,
            meliponarioId: Long,
            startDate: Instant?,
            initialStatusId: Long,
        ): ColmeiaSummary = error("unused")
        override fun appendStatus(append: AppendColmeiaStatus) = null
        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long) = false
    }

    private object FakeStatusLookup : StatusColmeiaLookupPort {
        override fun findIdByName(name: String): Long? = when (name) {
            "perdida" -> 4L
            "vendida" -> 6L
            else -> null
        }
    }
}
