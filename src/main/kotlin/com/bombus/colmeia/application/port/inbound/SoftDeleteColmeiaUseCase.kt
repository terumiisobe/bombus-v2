package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary

interface SoftDeleteColmeiaUseCase {
    fun softDelete(command: SoftDeleteColmeiaCommand): ColmeiaSummary
}

/**
 * Soft-delete appends status "perdida" (never DELETE). Idempotent if already perdida.
 * That frees [ColmeiaSummary.code] for reuse among non-perdida hives in the meliponário.
 */
data class SoftDeleteColmeiaCommand(
    val userId: Long,
    val colmeiaId: Long? = null,
    val code: Int? = null,
    val meliponarioId: Long? = null,
)
