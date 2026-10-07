package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary
import java.time.Instant

interface CreateColmeiaUseCase {
    fun create(command: CreateColmeiaCommand): ColmeiaSummary
}

/**
 * Field contract (create):
 * - speciesId: user (required)
 * - statusId: user optional; default "desenvolvendo"
 * - code: user optional; otherwise null (no auto-assign)
 * - startDate: user optional; otherwise null
 * - meliponarioId: derived; the lowest-id meliponário the user is a member of
 * - id: generated
 */
data class CreateColmeiaCommand(
    val userId: Long,
    val speciesId: Long,
    val statusId: Long? = null,
    val code: Int? = null,
    val startDate: Instant? = null,
)
