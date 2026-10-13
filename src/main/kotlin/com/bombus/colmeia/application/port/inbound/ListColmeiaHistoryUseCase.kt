package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry

/**
 * Last N status-history rows for one hive id.
 */
interface ListColmeiaHistoryUseCase {
    fun list(query: ListColmeiaHistoryQuery): List<ColmeiaStatusHistoryEntry>
}

data class ListColmeiaHistoryQuery(
    val userId: Long,
    val colmeiaId: Long,
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
    }
}
