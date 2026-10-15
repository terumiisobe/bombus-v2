package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.RecordColmeiaLocationCommand
import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.SaveLocalizacaoPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaCountFilter
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import com.bombus.colmeia.domain.LocalizacaoReading
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RecordColmeiaLocationServiceTest {

    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `appends location when hive is accessible`() {
        val owned = FakeOwnedPort().apply {
            byId[100L] = sampleHive(100L)
        }
        val save = RecordingSavePort()
        val service = RecordColmeiaLocationService(owned, save, clock)

        val reading = service.record(
            RecordColmeiaLocationCommand(
                userId = 1L,
                colmeiaId = 100L,
                latitude = BigDecimal("-23.550520"),
                longitude = BigDecimal("-46.633308"),
            ),
        )

        assertEquals(100L, reading.colmeiaId)
        assertEquals(now, reading.recordedAt)
        assertNotNull(save.last)
        assertEquals(1L, save.last!!.userId)
        assertEquals(100L, save.last!!.colmeiaId)
        assertEquals(BigDecimal("-23.550520"), save.last!!.latitude)
        assertEquals(BigDecimal("-46.633308"), save.last!!.longitude)
        assertEquals(now, save.last!!.recordedAt)
    }

    @Test
    fun `throws ColmeiaNotFound when hive is not accessible`() {
        val service = RecordColmeiaLocationService(FakeOwnedPort(), RecordingSavePort(), clock)

        assertThrows<ColmeiaCommandError.ColmeiaNotFound> {
            service.record(
                RecordColmeiaLocationCommand(
                    userId = 1L,
                    colmeiaId = 100L,
                    latitude = BigDecimal("0"),
                    longitude = BigDecimal("0"),
                ),
            )
        }
    }

    @Test
    fun `rejects latitude out of range`() {
        val owned = FakeOwnedPort().apply { byId[100L] = sampleHive(100L) }
        val service = RecordColmeiaLocationService(owned, RecordingSavePort(), clock)

        assertThrows<IllegalArgumentException> {
            service.record(
                RecordColmeiaLocationCommand(
                    userId = 1L,
                    colmeiaId = 100L,
                    latitude = BigDecimal("91"),
                    longitude = BigDecimal("0"),
                ),
            )
        }
    }

    private fun sampleHive(id: Long) = ColmeiaSummary(
        id = id,
        code = 7,
        speciesId = 1L,
        speciesAbbreviation = "JT",
        speciesCommonName = "Jataí",
        meliponarioId = 10L,
        startDate = Instant.parse("2026-01-01T00:00:00Z"),
        statusId = 3L,
        statusName = "estavel",
    )

    private class RecordingSavePort : SaveLocalizacaoPort {
        var last: AppendLocalizacao? = null
            private set

        override fun append(command: AppendLocalizacao): LocalizacaoReading {
            last = command
            return LocalizacaoReading(
                id = 1L,
                colmeiaId = command.colmeiaId,
                latitude = command.latitude,
                longitude = command.longitude,
                accuracyMeters = command.accuracyMeters,
                recordedAt = command.recordedAt,
            )
        }
    }

    private class FakeOwnedPort : OwnedColmeiaPort {
        val byId = mutableMapOf<Long, ColmeiaSummary>()

        override fun listMeliponarioIdsByOwner(userId: Long): List<Long> = emptyList()

        override fun listByOwner(
            userId: Long,
            filter: ColmeiaCountFilter,
            limit: Int,
            offset: Int,
        ): List<ColmeiaSummary> = emptyList()

        override fun findByCodeForOwner(userId: Long, code: Int): List<ColmeiaSummary> = emptyList()

        override fun findByIdForOwner(userId: Long, colmeiaId: Long): ColmeiaSummary? = byId[colmeiaId]

        override fun listStatusHistory(
            userId: Long,
            colmeiaId: Long,
            limit: Int,
        ): List<ColmeiaStatusHistoryEntry> = emptyList()

        override fun isCodeTaken(
            meliponarioId: Long,
            code: Int,
            exceptColmeiaId: Long?,
            ignoreStatusIds: Collection<Long>,
        ): Boolean = false

        override fun insert(
            code: Int?,
            speciesId: Long,
            meliponarioId: Long,
            startDate: Instant?,
            initialStatusId: Long,
            recordedByUserId: Long,
        ): ColmeiaSummary = error("not used")

        override fun appendStatus(append: com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus) = null

        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long): Boolean = false
    }
}
