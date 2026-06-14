package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.outbound.ColmeiaCountPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaCountFilter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CountColmeiasService(
    private val countPort: ColmeiaCountPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
) : CountColmeiasUseCase {

    override fun count(query: CountColmeiasQuery): ColmeiaCount {
        require(query.groupBy.isEmpty()) {
            "Grouped breakdown is not supported yet"
        }
        val filter = toFilter(query)
        return ColmeiaCount(total = countPort.countByOwner(query.userId, filter))
    }

    private fun toFilter(query: CountColmeiasQuery): ColmeiaCountFilter =
        if (query.statusId != null) {
            ColmeiaCountFilter(speciesId = query.speciesId, includeStatusId = query.statusId)
        } else {
            ColmeiaCountFilter(
                speciesId = query.speciesId,
                excludeStatusId = statusLookupPort.findIdByName(properties.defaultExcludedStatus),
            )
        }
}
