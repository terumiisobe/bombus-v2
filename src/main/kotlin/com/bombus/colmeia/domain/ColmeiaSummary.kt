package com.bombus.colmeia.domain

import java.time.Instant

/**
 * Hive snapshot. WhatsApp tool payloads expose code + species common name + status name only;
 * [id] and other fields stay internal for persistence/use cases.
 */
data class ColmeiaSummary(
    val id: Long,
    val code: Int?,
    val speciesId: Long,
    val speciesAbbreviation: String,
    val speciesCommonName: String,
    val meliponarioId: Long,
    val startDate: Instant?,
    val statusId: Long?,
    val statusName: String?,
)
