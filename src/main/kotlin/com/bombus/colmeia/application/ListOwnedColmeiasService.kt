package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ListOwnedColmeiasService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
) : ListOwnedColmeiasUseCase {

    override fun list(query: ListOwnedColmeiasQuery): List<ColmeiaSummary> {
        if (query.limit !in 1..ListOwnedColmeiasQuery.MAX_LIMIT) {
            throw ColmeiaCommandError.InvalidLimit()
        }
        if (query.offset < 0) {
            throw ColmeiaCommandError.InvalidLimit()
        }
        val excludeStatusId =
            if (query.includeLost) null
            else statusLookupPort.findIdByName(properties.defaultExcludedStatus)
        return ownedColmeiaPort.listByOwner(
            userId = query.userId,
            excludeStatusId = excludeStatusId,
            limit = query.limit,
            offset = query.offset,
        )
    }
}
