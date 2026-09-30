package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary

interface ListOwnedColmeiasUseCase {
    fun list(query: ListOwnedColmeiasQuery): List<ColmeiaSummary>
}

data class ListOwnedColmeiasQuery(
    val userId: Long,
    val includeLost: Boolean = false,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
    }
}
