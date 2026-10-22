package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.LocalizacaoReading
import java.math.BigDecimal
import java.time.Instant

/**
 * Appends localizacao rows for a hive the user can access (membership-scoped).
 * Returns null when the colmeia is not accessible to the given user.
 *
 * Pins and clear events are both append-only; current presence = latest row.
 */
interface SaveLocalizacaoPort {
    /** Live pin: both coordinates required. */
    fun append(command: AppendLocalizacao): LocalizacaoReading?

    /**
     * Clear event: no current pin (coords null). Prior pin rows stay for history.
     * Used by visit-2 code liberation; D1 exposes the port for uniqueness tests.
     */
    fun appendClear(command: AppendLocalizacaoClear): LocalizacaoReading?
}

data class AppendLocalizacao(
    val userId: Long,
    val colmeiaId: Long,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val recordedAt: Instant,
)

data class AppendLocalizacaoClear(
    val userId: Long,
    val colmeiaId: Long,
    val recordedAt: Instant,
)
