package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaSummary

interface ListOwnedColmeiasUseCase {
    fun list(query: ListOwnedColmeiasQuery): List<ColmeiaSummary>
}

/**
 * Bounded membership-scoped hive sample (not a count).
 * Filter rules match [CountColmeiasQuery]: a named [statusId] includes only that status;
 * otherwise default-excluded statuses apply unless [includeLost] is true.
 */
data class ListOwnedColmeiasQuery(
    val userId: Long,
    val speciesId: Long? = null,
    val statusId: Long? = null,
    val includeLost: Boolean = false,
    val limit: Int = DEFAULT_LIMIT,
    val offset: Int = 0,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
    }
}
