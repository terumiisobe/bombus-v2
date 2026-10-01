package com.bombus.colmeia.application.port.inbound

/**
 * Hard-delete an owned hive by code. Callers (chat tools) must obtain user confirmation first.
 */
interface DeleteColmeiaUseCase {
    fun delete(command: DeleteColmeiaCommand): DeletedColmeia
}

data class DeleteColmeiaCommand(
    val userId: Long,
    val code: Int,
)

data class DeletedColmeia(
    val code: Int?,
    val speciesCommonName: String,
    val statusName: String?,
)
