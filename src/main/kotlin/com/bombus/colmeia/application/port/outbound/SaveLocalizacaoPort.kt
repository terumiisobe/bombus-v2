package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.LocalizacaoReading
import java.math.BigDecimal
import java.time.Instant

/**
 * Appends a GPS reading for a hive the user can access (membership-scoped).
 * Returns null when [AppendLocalizacao.colmeiaId] is not accessible to [AppendLocalizacao.userId].
 */
interface SaveLocalizacaoPort {
    fun append(command: AppendLocalizacao): LocalizacaoReading?
}

data class AppendLocalizacao(
    val userId: Long,
    val colmeiaId: Long,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val accuracyMeters: BigDecimal? = null,
    val recordedAt: Instant,
)
