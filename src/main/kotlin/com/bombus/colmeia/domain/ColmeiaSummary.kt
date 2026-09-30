package com.bombus.colmeia.domain

import java.time.Instant

/**
 * Concise hive snapshot for WhatsApp list/create/update replies.
 * Location is intentionally omitted (bot does not manage it).
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
