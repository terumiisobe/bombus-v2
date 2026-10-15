package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapQuery
import com.bombus.colmeia.application.port.outbound.LoadMeliponarioMapPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.MeliponarioMapHive
import com.bombus.colmeia.domain.MeliponarioMapView
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GetMeliponarioMapServiceTest {

    @Test
    fun `returns farm view from port`() {
        val view = MeliponarioMapView(
            meliponarioId = 10,
            farmName = "Meliponário Isobe",
            address = "Estrada do Mel 123",
            hives = listOf(
                MeliponarioMapHive(
                    code = 12,
                    latitude = BigDecimal("-23.947800"),
                    longitude = BigDecimal("-46.986600"),
                    statusName = "estavel",
                    addedAt = LocalDate.parse("2025-06-30"),
                    addedAtInstant = Instant.parse("2025-06-30T00:00:00Z"),
                    lastInspectionAt = Instant.parse("2026-10-09T15:00:00Z"),
                    notes = "Trocar o fundo.",
                    locationRecordedAt = Instant.parse("2026-10-01T12:00:00Z"),
                ),
            ),
        )
        val service = GetMeliponarioMapService(FakePort(view))

        val result = service.get(GetMeliponarioMapQuery(userId = 1, meliponarioId = 10))

        assertEquals("Meliponário Isobe", result.farmName)
        assertEquals("Estrada do Mel 123", result.address)
        assertEquals(1, result.hives.size)
        assertEquals(12, result.hives[0].code)
        assertEquals(Instant.parse("2026-10-09T15:00:00Z"), result.updatedAt())
    }

    @Test
    fun `updatedAt prefers latest among start inspection and location`() {
        val view = MeliponarioMapView(
            meliponarioId = 10,
            farmName = "Yard",
            address = "addr",
            hives = listOf(
                MeliponarioMapHive(
                    code = 1,
                    latitude = null,
                    longitude = null,
                    statusName = "desenvolvendo",
                    addedAt = LocalDate.parse("2024-01-01"),
                    addedAtInstant = Instant.parse("2024-01-01T00:00:00Z"),
                    lastInspectionAt = Instant.parse("2026-01-01T00:00:00Z"),
                    notes = null,
                    locationRecordedAt = Instant.parse("2026-05-01T00:00:00Z"),
                ),
            ),
        )

        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), view.updatedAt())
    }

    @Test
    fun `updatedAt is null when farm has no timestamps`() {
        val view = MeliponarioMapView(
            meliponarioId = 10,
            farmName = "Empty",
            address = "addr",
            hives = emptyList(),
        )
        assertNull(view.updatedAt())
    }

    @Test
    fun `missing or inaccessible yard is not found`() {
        assertFailsWith<ColmeiaCommandError.MeliponarioNotFound> {
            GetMeliponarioMapService(FakePort(null))
                .get(GetMeliponarioMapQuery(userId = 1, meliponarioId = 99))
        }
    }

    private class FakePort(
        private val view: MeliponarioMapView?,
    ) : LoadMeliponarioMapPort {
        override fun loadForMember(userId: Long, meliponarioId: Long): MeliponarioMapView? = view
    }
}
