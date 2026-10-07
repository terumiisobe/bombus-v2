package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary

/**
 * Records a field visit / status observation for an owned hive identified by code.
 * Always appends a historico row (including same-status confirmations).
 */
interface RecordColmeiaStatusUseCase {
    fun record(command: RecordColmeiaStatusCommand): ColmeiaSummary
}

/**
 * Identify by code — must match exactly one **active** hive
 * (perdida/vendida rows with the same code are ignored and never updated).
 * [userId] is the actor (audit via recorded_by_user_id) and ownership scope.
 * Optional [note] is a short field remark.
 */
data class RecordColmeiaStatusCommand(
    val userId: Long,
    val code: Int,
    val statusId: Long,
    val note: String? = null,
)
