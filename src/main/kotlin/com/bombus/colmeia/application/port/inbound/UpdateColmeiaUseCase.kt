package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant

interface UpdateColmeiaUseCase {
    fun update(command: UpdateColmeiaCommand): ColmeiaSummary
}

/**
 * Field contract (update): identify by colmeiaId (preferred) or code (+ optional meliponarioId).
 * Mutable: speciesId, statusId (append history), startDate. Code and meliponario stay fixed.
 */
data class UpdateColmeiaCommand(
    val userId: Long,
    val colmeiaId: Long? = null,
    val code: Int? = null,
    val meliponarioId: Long? = null,
    val speciesId: Long? = null,
    val statusId: Long? = null,
    val startDate: Instant? = null,
)
