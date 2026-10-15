package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.LocalizacaoReading
import java.math.BigDecimal

/**
 * Registers a GPS location on one hive id (append-only registry update).
 * The caller resolves the user's code to [colmeiaId] first. Not a field-visit / status write.
 */
interface RecordColmeiaLocationUseCase {
    fun record(command: RecordColmeiaLocationCommand): LocalizacaoReading
}

data class RecordColmeiaLocationCommand(
    val userId: Long,
    val colmeiaId: Long,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
)
