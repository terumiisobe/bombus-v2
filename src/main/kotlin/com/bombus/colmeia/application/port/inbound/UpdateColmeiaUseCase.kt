package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary

interface UpdateColmeiaUseCase {
    fun update(command: UpdateColmeiaCommand): ColmeiaSummary
}

/**
 * Field contract (update): identify by code.
 * Mutable: statusId only (append history). Everything else is fixed.
 */
data class UpdateColmeiaCommand(
    val userId: Long,
    val code: Int,
    val statusId: Long,
)
