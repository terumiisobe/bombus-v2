package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryQuery
import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryUseCase
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ListColmeiaHistoryService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
) : ListColmeiaHistoryUseCase {

    override fun list(query: ListColmeiaHistoryQuery): List<ColmeiaStatusHistoryEntry> {
        if (query.limit !in 1..ListColmeiaHistoryQuery.MAX_LIMIT) {
            throw ColmeiaCommandError.InvalidLimit()
        }
        if (ownedColmeiaPort.findByIdForOwner(query.userId, query.colmeiaId) == null) {
            throw ColmeiaCommandError.ColmeiaNotFound()
        }
        return ownedColmeiaPort.listStatusHistory(
            userId = query.userId,
            colmeiaId = query.colmeiaId,
            limit = query.limit,
        )
    }
}
