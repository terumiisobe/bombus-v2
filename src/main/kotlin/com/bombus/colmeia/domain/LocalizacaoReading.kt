package com.bombus.colmeia.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * One GPS registration for a hive. Rows are append-only; the current location is the
 * latest by [recordedAt] (and id) for the same [colmeiaId].
 */
data class LocalizacaoReading(
    val id: Long,
    val colmeiaId: Long,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal?,
    val recordedAt: Instant,
)
