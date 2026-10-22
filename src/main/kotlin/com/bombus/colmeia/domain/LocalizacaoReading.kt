package com.bombus.colmeia.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * One localizacao row for a hive. Rows are append-only; the current presence is the
 * latest by [recordedAt] (and id) for the same [colmeiaId].
 *
 * - Both [latitude] and [longitude] set → live pin.
 * - Both null → clear event (no current pin; history of prior pins kept).
 * - DB check enforces both-or-neither; never one null.
 */
data class LocalizacaoReading(
    val id: Long,
    val colmeiaId: Long,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val recordedAt: Instant,
) {
    val isClearEvent: Boolean
        get() = latitude == null && longitude == null

    val isLivePin: Boolean
        get() = latitude != null && longitude != null

    init {
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be set (live pin) or both null (clear event)"
        }
    }
}
