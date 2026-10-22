package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.LocalizacaoReading

/**
 * Visit 2 — “código liberado”: append a clear event on a disposed hive that still
 * has a live pin. Soft-releases the code via pin-gated uniqueness (D1). Caller
 * resolves code → [colmeiaId] and handles soft confirmation.
 */
interface ReleaseColmeiaCodeUseCase {
    fun release(command: ReleaseColmeiaCodeCommand): LocalizacaoReading
}

data class ReleaseColmeiaCodeCommand(
    val userId: Long,
    val colmeiaId: Long,
)
