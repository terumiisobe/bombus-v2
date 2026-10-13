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

    @Test
    fun `returns newest visits for the hive id`() {
        val port = FakePort(
            hive = summary(id = 10),
            history = listOf(
                ColmeiaStatusHistoryEntry(Instant.parse("2026-10-01T10:00:00Z"), "estavel", "forte"),
                ColmeiaStatusHistoryEntry(Instant.parse("2026-09-01T10:00:00Z"), "desenvolvendo", null),
            ),
        )

        val result = ListColmeiaHistoryService(port)
            .list(ListColmeiaHistoryQuery(userId = OWNER, colmeiaId = 10, limit = 5))

        assertEquals(2, result.size)
        assertEquals("forte", result[0].note)
        assertEquals(10L, port.lastColmeiaId)
        assertEquals(5, port.lastLimit)
    }

    @Test
    fun `throws not found when the id is not accessible`() {
        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            ListColmeiaHistoryService(FakePort(hive = null))
                .list(ListColmeiaHistoryQuery(userId = OWNER, colmeiaId = 99))
        }
    }

    @Test
    fun `rejects limit outside 1 to 50`() {
        val service = ListColmeiaHistoryService(FakePort(hive = summary(id = 1)))

        assertFailsWith<ColmeiaCommandError.InvalidLimit> {
            service.list(ListColmeiaHistoryQuery(userId = OWNER, colmeiaId = 1, limit = 0))
        }
        assertFailsWith<ColmeiaCommandError.InvalidLimit> {
            service.list(ListColmeiaHistoryQuery(userId = OWNER, colmeiaId = 1, limit = 51))
        }
    }

    private fun summary(id: Long) = ColmeiaSummary(
        id = id,
        code = 7,
        speciesId = 1L,
        speciesAbbreviation = "JT",
        speciesCommonName = "Jataí",
        meliponarioId = 10L,
        startDate = null,
        statusId = 3L,
        statusName = "estavel",
    )

    private class FakePort(
        private val hive: ColmeiaSummary?,
        private val history: List<ColmeiaStatusHistoryEntry> = emptyList(),
    ) : OwnedColmeiaPort {
        var lastColmeiaId: Long? = null
        var lastLimit: Int? = null

        override fun listMeliponarioIdsByOwner(userId: Long) = emptyList<Long>()
        override fun listByOwner(userId: Long, filter: com.bombus.colmeia.domain.ColmeiaCountFilter, limit: Int, offset: Int) =
            emptyList<ColmeiaSummary>()
        override fun findByCodeForOwner(userId: Long, code: Int) = emptyList<ColmeiaSummary>()
        override fun findByIdForOwner(userId: Long, colmeiaId: Long) = hive?.takeIf { it.id == colmeiaId }
        override fun listStatusHistory(userId: Long, colmeiaId: Long, limit: Int): List<ColmeiaStatusHistoryEntry> {
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
            recordedByUserId: Long,
        ) = error("unused")
        override fun appendStatus(append: AppendColmeiaStatus) = null
        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long) = false
    }

    private companion object {
        const val OWNER = 1L
    }
}
