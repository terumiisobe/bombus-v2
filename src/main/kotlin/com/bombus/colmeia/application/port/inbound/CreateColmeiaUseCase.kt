package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant

interface CreateColmeiaUseCase {
    fun create(command: CreateColmeiaCommand): ColmeiaSummary
}

/**
 * Field contract (create):
 * - speciesId: user (required)
 * - statusId: user optional; default "estavel"
 * - code: app next-free among non-perdida; user optional override if free
 * - meliponarioId: app derives owner's lowest id; user optional if owned
 * - startDate: app default now; user optional override
 * - id: generated
 */
data class CreateColmeiaCommand(
    val userId: Long,
    val speciesId: Long,
    val statusId: Long? = null,
    val code: Int? = null,
    val meliponarioId: Long? = null,
    val startDate: Instant? = null,
)
