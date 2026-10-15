package com.bombus.colmeia.adapter.inbound.web

import com.bombus.colmeia.domain.MeliponarioMapHive
import com.bombus.colmeia.domain.MeliponarioMapView
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Schema(description = "Farm snapshot for the map UI")
data class MeliponarioMapResponse(
    @Schema(example = "Meliponário Isobe")
    val farmName: String,
    @Schema(
        example = "Estrada do Mel 123, São Paulo - SP",
        description = "Free-text farm address from meliponario.address (DB NOT NULL; always present as string)",
    )
    val address: String,
    @Schema(example = "2026-10-15T12:00:00Z")
    val updatedAt: Instant?,
    val hives: List<MeliponarioMapHiveResponse>,
)

@Schema(description = "Hive pin + status for the map. Hives without a GPS pin are kept with null lat/lng.")
data class MeliponarioMapHiveResponse(
    @Schema(example = "12", description = "Hive code as string; null when the colmeia has no code")
    val code: String?,
    @Schema(example = "-23.9478", nullable = true)
    val lat: Double?,
    @Schema(example = "-46.9866", nullable = true)
    val lng: Double?,
    @Schema(
        example = "stable",
        description = "Mapped API status string (see MeliponarioMapStatusMapper)",
        allowableValues = ["developing", "recovering", "stable", "lost", "unknown", "sold"],
    )
    val status: String?,
    @Schema(example = "2025-06-30", description = "Colony start date (UTC date-only)")
    val addedAt: LocalDate?,
    @Schema(example = "2026-10-09", description = "Latest status historico date (UTC date-only); null if none")
    val lastInspection: LocalDate?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(example = "Trocar o fundo.", description = "Latest historico note only; omitted when null/blank")
    val notes: String?,
)

internal fun MeliponarioMapView.toResponse(): MeliponarioMapResponse =
    MeliponarioMapResponse(
        farmName = farmName,
        address = address,
        updatedAt = updatedAt(),
        hives = hives.map { it.toResponse() },
    )

internal fun MeliponarioMapHive.toResponse(): MeliponarioMapHiveResponse =
    MeliponarioMapHiveResponse(
        code = code?.toString(),
        lat = latitude?.toDouble(),
        lng = longitude?.toDouble(),
        status = MeliponarioMapStatusMapper.toApiStatus(statusName),
        addedAt = addedAt,
        lastInspection = lastInspectionAt?.atZone(ZoneOffset.UTC)?.toLocalDate(),
        notes = notes?.takeIf { it.isNotBlank() },
    )

/**
 * Maps canonical `status_colmeia.name` values to stable English API strings for the map UI.
 * Unknown / unexpected DB names pass through unchanged so new statuses are not silently dropped.
 */
object MeliponarioMapStatusMapper {
    private val DOMAIN_TO_API = mapOf(
        "desenvolvendo" to "developing",
        "recuperando" to "recovering",
        "estavel" to "stable",
        "perdida" to "lost",
        "desconhecido" to "unknown",
        "vendida" to "sold",
    )

    fun toApiStatus(domainName: String?): String? =
        domainName?.let { DOMAIN_TO_API[it] ?: it }
}
