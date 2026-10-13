package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.DetailColmeiaQuery
import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DetailColmeiaServiceTest {

    @Test
    fun `returns code species status and last visit`() {
        val visit = ColmeiaStatusHistoryEntry(Instant.parse("2026-10-08T15:26:10Z"), "estavel", "colônia forte")
        val service = DetailColmeiaService(FakePort(hive = HIVE, visit = visit))

        val detail = service.detail(DetailColmeiaQuery(userId = 1, colmeiaId = 100))

        assertEquals(7, detail.code)
        assertEquals("Jataí", detail.speciesCommonName)
        assertEquals("estavel", detail.statusName)
        assertEquals("colônia forte", detail.lastVisit?.note)
    }

    @Test
    fun `last visit is null when the hive has no history`() {
        val detail = DetailColmeiaService(FakePort(hive = HIVE, visit = null))
            .detail(DetailColmeiaQuery(userId = 1, colmeiaId = 100))

        assertNull(detail.lastVisit)
    }

    @Test
    fun `unknown id is not found`() {
        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            DetailColmeiaService(FakePort(hive = null, visit = null))
                .detail(DetailColmeiaQuery(userId = 1, colmeiaId = 100))
        }
    }

    private class FakePort(
        private val hive: ColmeiaSummary?,
        private val visit: ColmeiaStatusHistoryEntry?,
    ) : OwnedColmeiaPort {
        override fun listMeliponarioIdsByOwner(userId: Long) = emptyList<Long>()
        override fun listByOwner(userId: Long, excludeStatusIds: Collection<Long>, limit: Int, offset: Int) =
            emptyList<ColmeiaSummary>()
        override fun findByCodeForOwner(userId: Long, code: Int) = emptyList<ColmeiaSummary>()
        override fun findByIdForOwner(userId: Long, colmeiaId: Long) = hive
        override fun listStatusHistory(userId: Long, colmeiaId: Long, limit: Int) =
            listOfNotNull(visit).take(limit)
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
        val HIVE = ColmeiaSummary(
            id = 100,
            code = 7,
            speciesId = 1,
            speciesAbbreviation = "JT",
            speciesCommonName = "Jataí",
            meliponarioId = 10,
            startDate = null,
            statusId = 3,
            statusName = "estavel",
        )
    }
}
