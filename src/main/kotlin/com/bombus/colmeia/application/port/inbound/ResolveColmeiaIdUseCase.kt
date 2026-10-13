package com.bombus.colmeia.application.port.inbound

/**
 * Turns the code the user typed into one colmeia id. The id is the only write key.
 */
interface ResolveColmeiaIdUseCase {
    fun resolve(query: ResolveColmeiaQuery): ResolvedColmeia
}

data class ResolveColmeiaQuery(
    val userId: Long,
    val code: Int,
    val statusId: Long? = null,
)

data class ResolvedColmeia(
    val colmeiaId: Long,
    val inactive: Boolean,
    val statusName: String,
)
