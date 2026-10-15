package com.bombus.colmeia.adapter.inbound.web

import com.bombus.adapter.inbound.web.WebExceptionHandler
import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapQuery
import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapUseCase
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.MeliponarioMapHive
import com.bombus.colmeia.domain.MeliponarioMapView
import com.bombus.config.BombusApplication
import com.bombus.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@WebMvcTest(MeliponarioMapController::class)
@ContextConfiguration(classes = [BombusApplication::class])
@Import(SecurityConfig::class, WebExceptionHandler::class)
class MeliponarioMapControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockitoBean
    private lateinit var getMeliponarioMap: GetMeliponarioMapUseCase

    @Test
    fun `maps domain view to map JSON including null lat lng and english status`() {
        given(getMeliponarioMap.get(GetMeliponarioMapQuery(userId = 1, meliponarioId = 10)))
            .willReturn(
                MeliponarioMapView(
                    meliponarioId = 10,
                    farmName = "Meliponário Isobe",
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
                        MeliponarioMapHive(
                            code = 13,
                            latitude = null,
                            longitude = null,
                            statusName = "desenvolvendo",
                            addedAt = null,
                            addedAtInstant = null,
                            lastInspectionAt = null,
                            notes = null,
                            locationRecordedAt = null,
                        ),
                    ),
                ),
            )

        mockMvc.get("/v1/meliponarios/10") {
            header(MeliponarioMapController.USER_ID_HEADER, "1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.farmName") { value("Meliponário Isobe") }
            jsonPath("$.updatedAt") { value("2026-10-09T15:00:00Z") }
            jsonPath("$.hives[0].code") { value("12") }
            jsonPath("$.hives[0].lat") { value(-23.9478) }
            jsonPath("$.hives[0].lng") { value(-46.9866) }
            jsonPath("$.hives[0].status") { value("stable") }
            jsonPath("$.hives[0].addedAt") { value("2025-06-30") }
            jsonPath("$.hives[0].lastInspection") { value("2026-10-09") }
            jsonPath("$.hives[0].notes") { value("Trocar o fundo.") }
            jsonPath("$.hives[1].code") { value("13") }
            jsonPath("$.hives[1].lat") { value(null) }
            jsonPath("$.hives[1].lng") { value(null) }
            jsonPath("$.hives[1].status") { value("developing") }
            jsonPath("$.hives[1].notes") { doesNotExist() }
        }
    }

    @Test
    fun `missing user header is unauthorized`() {
        mockMvc.get("/v1/meliponarios/10")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `unknown yard is not found`() {
        given(getMeliponarioMap.get(GetMeliponarioMapQuery(userId = 1, meliponarioId = 99)))
            .willAnswer { throw ColmeiaCommandError.MeliponarioNotFound() }

        mockMvc.get("/v1/meliponarios/99") {
            header(MeliponarioMapController.USER_ID_HEADER, "1")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.title") { value("Not Found") }
        }
    }
}
