package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryQuery
import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ListColmeiaHistoryServiceTest {

    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `returns newest visits for the sole hive matching the code`() {
        val port = FakePort(
            matches = listOf(summary(id = 10, status = "estavel")),
            history = listOf(
                ColmeiaStatusHistoryEntry(Instant.parse("2026-10-01T10:00:00Z"), "estavel", "forte"),
                ColmeiaStatusHistoryEntry(Instant.parse("2026-09-01T10:00:00Z"), "desenvolvendo", null),
            ),
        )
        val service = ListColmeiaHistoryService(port, properties)

        val result = service.list(ListColmeiaHistoryQuery(userId = OWNER, code = 7, limit = 5))

        assertEquals(2, result.size)
        assertEquals("estavel", result[0].statusName)
        assertEquals("forte", result[0].note)
        assertEquals(10L, port.lastColmeiaId)
        assertEquals(5, port.lastLimit)
        assertEquals(OWNER, port.lastUserId)
    }

    @Test
    fun `prefers the active hive when code was reused after soft disposition`() {
        val port = FakePort(
            matches = listOf(
                summary(id = 1, status = "vendida"),
                summary(id = 2, status = "desenvolvendo"),
            ),
            history = listOf(
                ColmeiaStatusHistoryEntry(Instant.parse("2026-10-02T00:00:00Z"), "desenvolvendo", null),
            ),
        )

        val result = ListColmeiaHistoryService(port, properties)
            .list(ListColmeiaHistoryQuery(userId = OWNER, code = 7))

        assertEquals(1, result.size)
        assertEquals(2L, port.lastColmeiaId)
    }

    @Test
    fun `reads history for a sole soft-disposed hive`() {
        val port = FakePort(
            matches = listOf(summary(id = 9, status = "perdida")),
            history = listOf(
                ColmeiaStatusHistoryEntry(Instant.parse("2026-08-01T00:00:00Z"), "perdida", "sumiu"),
            ),
        )

        val result = ListColmeiaHistoryService(port, properties)
            .list(ListColmeiaHistoryQuery(userId = OWNER, code = 7))

        assertEquals(9L, port.lastColmeiaId)
        assertEquals("perdida", result.single().statusName)
    }

    @Test
    fun `throws not found when code matches nothing accessible`() {
        val service = ListColmeiaHistoryService(FakePort(matches = emptyList()), properties)

        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            service.list(ListColmeiaHistoryQuery(userId = OWNER, code = 99))
        }
    }

    @Test
    fun `throws ambiguous when multiple active hives share the code`() {
        val service = ListColmeiaHistoryService(
            FakePort(
                matches = listOf(
                    summary(id = 1, status = "estavel"),
                    summary(id = 2, status = "desenvolvendo"),
                ),
            ),
            properties,
        )

        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            service.list(ListColmeiaHistoryQuery(userId = OWNER, code = 7))
        }
    }

    @Test
    fun `rejects limit outside 1 to 50`() {
        val service = ListColmeiaHistoryService(
            FakePort(matches = listOf(summary(id = 1, status = "estavel"))),
            properties,
        )

        assertFailsWith<ColmeiaCommandError.InvalidLimit> {
            service.list(ListColmeiaHistoryQuery(userId = OWNER, code = 7, limit = 0))
        }
        assertFailsWith<ColmeiaCommandError.InvalidLimit> {
            service.list(ListColmeiaHistoryQuery(userId = OWNER, code = 7, limit = 51))
        }
    }

    private fun summary(id: Long, status: String) = ColmeiaSummary(
        id = id,
        code = 7,
        speciesId = 1L,
        speciesAbbreviation = "JT",
        speciesCommonName = "Jataí",
        meliponarioId = 10L,
        startDate = null,
        statusId = 1L,
        statusName = status,
    )

    private class FakePort(
        private val matches: List<ColmeiaSummary>,
        private val history: List<ColmeiaStatusHistoryEntry> = emptyList(),
    ) : OwnedColmeiaPort {
        var lastUserId: Long? = null
        var lastColmeiaId: Long? = null
        var lastLimit: Int? = null

        override fun listMeliponarioIdsByOwner(userId: Long) = emptyList<Long>()
        override fun listByOwner(userId: Long, excludeStatusIds: Collection<Long>, limit: Int, offset: Int) =
            emptyList<ColmeiaSummary>()
        override fun findByCodeForOwner(userId: Long, code: Int) = matches
        override fun listStatusHistory(userId: Long, colmeiaId: Long, limit: Int): List<ColmeiaStatusHistoryEntry> {
            lastUserId = userId
            lastColmeiaId = colmeiaId
            lastLimit = limit
            return history.take(limit)
        }
        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            exceptColmeiaId: Long?,
            ignoreStatusIds: Collection<Long>,
        ) = false
        override fun insert(
            code: Int?,
            speciesId: Long,
            meliponarioId: Long,
            startDate: Instant?,
            initialStatusId: Long,
        ) = error("unused")
        override fun appendStatus(append: AppendColmeiaStatus) = null
        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long) = false
    }

    private companion object {
        const val OWNER = 1L
    }
}
