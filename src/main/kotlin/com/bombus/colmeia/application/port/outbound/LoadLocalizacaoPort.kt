package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.LocalizacaoReading

/**
 * Reads the current (latest) localizacao row for a hive the user can access.
 * Returns null when the hive is inaccessible or has never been pinned.
 */
interface LoadLocalizacaoPort {
    fun findCurrent(userId: Long, colmeiaId: Long): LocalizacaoReading?
}
