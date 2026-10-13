package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasQuery
import com.bombus.colmeia.application.port.inbound.ListOwnedColmeiasUseCase
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaCountFilter
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
        return ownedColmeiaPort.listByOwner(
            userId = query.userId,
            filter = toFilter(query),
            limit = query.limit,
            offset = query.offset,
        )
    }

    private fun toFilter(query: ListOwnedColmeiasQuery): ColmeiaCountFilter =
        when {
            query.statusId != null ->
                ColmeiaCountFilter(speciesId = query.speciesId, includeStatusId = query.statusId)
            query.includeLost ->
                ColmeiaCountFilter(speciesId = query.speciesId)
            else ->
                ColmeiaCountFilter(
                    speciesId = query.speciesId,
                    excludeStatusIds = defaultExcludedIds(),
                )
        }

    private fun defaultExcludedIds(): Set<Long> =
        properties.defaultExcludedStatuses.mapNotNull { statusLookupPort.findIdByName(it) }.toSet()
}
