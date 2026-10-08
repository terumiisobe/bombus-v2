package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryQuery
import com.bombus.colmeia.application.port.inbound.ListColmeiaHistoryUseCase
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ListColmeiaHistoryService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val properties: ColmeiaCountProperties,
) : ListColmeiaHistoryUseCase {

    override fun list(query: ListColmeiaHistoryQuery): List<ColmeiaStatusHistoryEntry> {
        if (query.limit !in 1..ListColmeiaHistoryQuery.MAX_LIMIT) {
            throw ColmeiaCommandError.InvalidLimit()
        }
        val hive = resolveByCode(query.userId, query.code)
        return ownedColmeiaPort.listStatusHistory(
            userId = query.userId,
            colmeiaId = hive.id,
            limit = query.limit,
        )
    }

    /**
     * Membership-scoped code resolution: sole match wins; if a code is reused after
     * soft disposition, prefer the single active hive (same rule as update/delete).
     */
    private fun resolveByCode(userId: Long, code: Int): ColmeiaSummary {
        val matches = ownedColmeiaPort.findByCodeForOwner(userId, code)
        when (matches.size) {
            0 -> throw ColmeiaCommandError.ColmeiaNotFound()
            1 -> return matches.first()
        }
        val active = matches.filterNot { properties.releasesCode(it.statusName) }
        return when (active.size) {
            1 -> active.first()
            0 -> throw ColmeiaCommandError.AmbiguousCode()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
    }
}
