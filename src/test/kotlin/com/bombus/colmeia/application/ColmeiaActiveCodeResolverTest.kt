package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColmeiaActiveCodeResolverTest {

    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `returns the sole active hive for the code`() {
        val active = summary(id = 1, status = "desenvolvendo")
        val sold = summary(id = 2, status = "vendida")
        val resolver = resolver(listOf(sold, active))

        assertEquals(1L, resolver.requireExactlyOneActive(OWNER, 7).id)
    }

    @Test
    fun `ignores sole perdida or vendida and reports not found`() {
        val resolver = resolver(listOf(summary(id = 1, status = "vendida")))

        assertFailsWith<ColmeiaCommandError.ColmeiaNotFound> {
            resolver.requireExactlyOneActive(OWNER, 7)
        }
    }

    @Test
    fun `ambiguous when two active hives share the code`() {
        val resolver = resolver(
            listOf(
                summary(id = 1, status = "estavel"),
                summary(id = 2, status = "desenvolvendo"),
            ),
        )

        assertFailsWith<ColmeiaCommandError.AmbiguousCode> {
            resolver.requireExactlyOneActive(OWNER, 7)
        }
    }

    private fun resolver(matches: List<ColmeiaSummary>) =
        ColmeiaActiveCodeResolver(FakePort(matches), properties)

    private fun summary(id: Long, status: String) = ColmeiaSummary(
        id = id,
        code = 7,
        speciesId = 1L,
        speciesAbbreviation = "JT",
        speciesCommonName = "Jataí",
        meliponarioId = 10L,
        startDate = Instant.parse("2026-01-01T00:00:00Z"),
        statusId = 1L,
        statusName = status,
    )

    private class FakePort(private val matches: List<ColmeiaSummary>) : OwnedColmeiaPort {
        override fun listMeliponarioIdsByOwner(userId: Long) = emptyList<Long>()
        override fun listByOwner(userId: Long, excludeStatusIds: Collection<Long>, limit: Int, offset: Int) =
            emptyList<ColmeiaSummary>()
        override fun findByCodeForOwner(userId: Long, code: Int) = matches
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
        override fun appendStatus(colmeiaId: Long, statusId: Long, recordedAt: Instant) = null
        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long) = false
    }

    private companion object {
        const val OWNER = 1L
    }
}
