package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary

/**
 * Records a field visit on one hive id. Always appends historico, including when
 * the status did not change. The caller resolves the user's code to [colmeiaId] first.
 */
interface RecordColmeiaStatusUseCase {
    fun record(command: RecordColmeiaStatusCommand): ColmeiaSummary
}

data class RecordColmeiaStatusCommand(
    val userId: Long,
    val colmeiaId: Long,
    val statusId: Long,
    val note: String? = null,
)
