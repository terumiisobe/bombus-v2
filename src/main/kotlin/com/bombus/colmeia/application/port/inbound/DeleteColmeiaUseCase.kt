package com.bombus.colmeia.application.port.inbound

/**
 * Hard-delete one owned hive by id. The caller resolves the user's code to that id first.
 */
interface DeleteColmeiaUseCase {
    fun delete(command: DeleteColmeiaCommand): DeletedColmeia
}

data class DeleteColmeiaCommand(
    val userId: Long,
    val colmeiaId: Long,
)

data class DeletedColmeia(
    val code: Int?,
    val speciesCommonName: String,
    val statusName: String?,
)
