package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ReleaseColmeiaCodeCommand
import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
import com.bombus.colmeia.application.port.outbound.AppendLocalizacaoClear
import com.bombus.colmeia.application.port.outbound.LoadLocalizacaoPort
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseColmeiaCodeServiceTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val properties = ColmeiaCountProperties(defaultExcludedStatuses = listOf("perdida", "vendida"))

    @Test
    fun `appends clear when disposed hive has live pin`() {
        val owned = FakeOwnedPort().apply { byId[100L] = disposedHive(100L, "vendida") }
        val load = FakeLoadPort(
            LocalizacaoReading(
                id = 1L,
                colmeiaId = 100L,
                latitude = BigDecimal("-23.5"),
                longitude = BigDecimal("-46.6"),
                recordedAt = Instant.parse("2026-10-01T00:00:00Z"),
            ),
        )
        val save = RecordingSavePort()
        val service = ReleaseColmeiaCodeService(owned, load, save, properties, clock)

        val reading = service.release(ReleaseColmeiaCodeCommand(userId = 1L, colmeiaId = 100L))

        assertTrue(reading.isClearEvent)
        assertEquals(now, reading.recordedAt)
        assertNotNull(save.lastClear)
        assertEquals(1L, save.lastClear!!.userId)
        assertEquals(100L, save.lastClear!!.colmeiaId)
        assertEquals(now, save.lastClear!!.recordedAt)
    }

    @Test
    fun `throws NoLivePinToClear when never pinned`() {
        val owned = FakeOwnedPort().apply { byId[100L] = disposedHive(100L, "perdida") }
        val service = ReleaseColmeiaCodeService(
            owned,
            FakeLoadPort(null),
            RecordingSavePort(),
            properties,
            clock,
        )

        assertThrows<ColmeiaCommandError.NoLivePinToClear> {
            service.release(ReleaseColmeiaCodeCommand(userId = 1L, colmeiaId = 100L))
        }
    }

    @Test
    fun `throws NoLivePinToClear when already cleared`() {
        val owned = FakeOwnedPort().apply { byId[100L] = disposedHive(100L, "vendida") }
        val load = FakeLoadPort(
            LocalizacaoReading(
                id = 2L,
                colmeiaId = 100L,
                latitude = null,
                longitude = null,
                recordedAt = Instant.parse("2026-10-10T00:00:00Z"),
            ),
        )
        val save = RecordingSavePort()
        val service = ReleaseColmeiaCodeService(owned, load, save, properties, clock)

        assertThrows<ColmeiaCommandError.NoLivePinToClear> {
            service.release(ReleaseColmeiaCodeCommand(userId = 1L, colmeiaId = 100L))
        }
        assertNull(save.lastClear)
    }

    @Test
    fun `throws NotDispositionStatus when hive is still active`() {
        val owned = FakeOwnedPort().apply { byId[100L] = activeHive(100L) }
        val load = FakeLoadPort(
            LocalizacaoReading(
                id = 1L,
                colmeiaId = 100L,
                latitude = BigDecimal("1"),
                longitude = BigDecimal("2"),
                recordedAt = now,
            ),
        )
        val save = RecordingSavePort()
        val service = ReleaseColmeiaCodeService(owned, load, save, properties, clock)

        assertThrows<ColmeiaCommandError.NotDispositionStatus> {
            service.release(ReleaseColmeiaCodeCommand(userId = 1L, colmeiaId = 100L))
        }
        assertNull(save.lastClear)
    }

    @Test
    fun `throws ColmeiaNotFound when hive is not accessible`() {
        val service = ReleaseColmeiaCodeService(
            FakeOwnedPort(),
            FakeLoadPort(null),
            RecordingSavePort(),
            properties,
            clock,
        )

        assertThrows<ColmeiaCommandError.ColmeiaNotFound> {
            service.release(ReleaseColmeiaCodeCommand(userId = 1L, colmeiaId = 100L))
        }
    }

    private fun disposedHive(id: Long, status: String) = ColmeiaSummary(
        id = id,
        code = 7,
        speciesId = 1L,
        speciesAbbreviation = "JT",
        speciesCommonName = "Jataí",
        meliponarioId = 10L,
        startDate = Instant.parse("2026-01-01T00:00:00Z"),
        statusId = if (status == "vendida") 6L else 4L,
        statusName = status,
    )

    private fun activeHive(id: Long) = ColmeiaSummary(
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

    private class FakeLoadPort(private val current: LocalizacaoReading?) : LoadLocalizacaoPort {
        override fun findCurrent(userId: Long, colmeiaId: Long): LocalizacaoReading? = current
    }

    private class RecordingSavePort : SaveLocalizacaoPort {
        var lastClear: AppendLocalizacaoClear? = null
            private set

        override fun append(command: AppendLocalizacao): LocalizacaoReading? = error("unused")

        override fun appendClear(command: AppendLocalizacaoClear): LocalizacaoReading {
            lastClear = command
            return LocalizacaoReading(
                id = 99L,
                colmeiaId = command.colmeiaId,
                latitude = null,
                longitude = null,
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
        ): ColmeiaSummary = error("unused")

        override fun appendStatus(append: com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus) = null

        override fun deleteByIdForOwner(userId: Long, colmeiaId: Long): Boolean = false
    }
}
