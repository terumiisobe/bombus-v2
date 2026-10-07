package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry

/**
 * Last N status-history (visit) rows for an owned hive identified by code.
 */
interface ListColmeiaHistoryUseCase {
    fun list(query: ListColmeiaHistoryQuery): List<ColmeiaStatusHistoryEntry>
}

/**
 * [userId] scopes membership access. [code] identifies the hive.
 * Prefer the single active hive when a code is reused after soft disposition.
 */
data class ListColmeiaHistoryQuery(
    val userId: Long,
    val code: Int,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
    }
}
