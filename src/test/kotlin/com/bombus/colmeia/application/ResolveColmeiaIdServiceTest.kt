package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ResolveColmeiaQuery
import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolveColmeiaIdServiceTest {

    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `sole vendida code resolves to that id and is inactive`() {
        val resolved = service(summary(id = 100, status = "vendida", statusId = 6))
            .resolve(ResolveColmeiaQuery(userId = 1, code = 7))

        assertEquals(100L, resolved.colmeiaId)
        assertTrue(resolved.inactive)
        assertEquals("vendida", resolved.statusName)
    }

    @Test
    fun `sole perdida code resolves to that id and is inactive`() {
        val resolved = service(summary(id = 9, status = "perdida", statusId = 4))
            .resolve(ResolveColmeiaQuery(userId = 1, code = 7))

        assertEquals(9L, resolved.colmeiaId)
        assertTrue(resolved.inactive)
    }

    @Test
    fun `active hive wins over a vendida sibling`() {
        val resolved = service(
            summary(id = 100, status = "vendida", statusId = 6),
            summary(id = 101, status = "desenvolvendo", statusId = 1),
        ).resolve(ResolveColmeiaQuery(userId = 1, code = 7))

        assertEquals(101L, resolved.colmeiaId)
        assertFalse(resolved.inactive)
    }

    @Test
    fun `named vendida status selects the inactive sibling`() {
        val resolved = service(
            summary(id = 100, status = "vendida", statusId = 6),
            summary(id = 101, status = "desenvolvendo", statusId = 1),
        ).resolve(ResolveColmeiaQuery(userId = 1, code = 7, statusId = 6))

        assertEquals(100L, resolved.colmeiaId)
        assertTrue(resolved.inactive)
    }

    @Test
    fun `named status that is not present is not found`() {
        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            service(summary(id = 101, status = "desenvolvendo", statusId = 1))
                .resolve(ResolveColmeiaQuery(userId = 1, code = 7, statusId = 4))
        }
    }

    @Test
    fun `two vendida rows and no active hive are ambiguous`() {
        assertFailsWith<ColmeiaCommandError.AmbiguousDisposition> {
            service(
                summary(id = 1, status = "vendida", statusId = 6),
                summary(id = 2, status = "vendida", statusId = 6),
            ).resolve(ResolveColmeiaQuery(userId = 1, code = 7))
        }
    }

    @Test
    fun `two active rows are ambiguous`() {
        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            service(
                summary(id = 1, status = "estavel", statusId = 3),
                summary(id = 2, status = "desenvolvendo", statusId = 1),
            ).resolve(ResolveColmeiaQuery(userId = 1, code = 7))
        }
    }

    @Test
    fun `unknown code is not found`() {
        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            service().resolve(ResolveColmeiaQuery(userId = 1, code = 99))
        }
    }

    private fun service(vararg matches: ColmeiaSummary) =
        ResolveColmeiaIdService(FakePort(matches.toList()), properties)

    private fun summary(id: Long, status: String, statusId: Long) = ColmeiaSummary(
        id = id,
        code = 7,
        speciesId = 1L,
        speciesAbbreviation = "JT",
        speciesCommonName = "Jataí",
        meliponarioId = 10L,
        startDate = null,
        statusId = statusId,
        statusName = status,
    )

    private class FakePort(private val matches: List<ColmeiaSummary>) : OwnedColmeiaPort {
        override fun listMeliponarioIdsByOwner(userId: Long) = emptyList<Long>()
        override fun listByOwner(userId: Long, filter: com.bombus.colmeia.domain.ColmeiaCountFilter, limit: Int, offset: Int) =
            emptyList<ColmeiaSummary>()
        override fun findByCodeForOwner(userId: Long, code: Int) = matches
        override fun listStatusHistory(userId: Long, colmeiaId: Long, limit: Int) = emptyList<ColmeiaStatusHistoryEntry>()
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
}
