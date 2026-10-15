package com.bombus.colmeia.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Membership-scoped farm snapshot for the map UI.
 * Status names are the canonical DB labels (`status_colmeia.name`); the web adapter maps them to API strings.
 */
data class MeliponarioMapView(
    val meliponarioId: Long,
    val farmName: String,
    val hives: List<MeliponarioMapHive>,
) {
    /** Aggregate freshness: max of hive start, last inspection, and location pin times. */
    fun updatedAt(): Instant? =
        hives
            .flatMap { hive ->
                listOfNotNull(hive.addedAtInstant, hive.lastInspectionAt, hive.locationRecordedAt)
            }
            .maxOrNull()
}

data class MeliponarioMapHive(
    val code: Int?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    /** Canonical `status_colmeia.name`, or null when the hive has no historico. */
    val statusName: String?,
    val addedAt: LocalDate?,
    /** Instant used only for farm-level [MeliponarioMapView.updatedAt]; not exposed on the wire as-is. */
    val addedAtInstant: Instant?,
    val lastInspectionAt: Instant?,
    /** Note from the latest status historico row, if any. */
    val notes: String?,
    val locationRecordedAt: Instant?,
)
