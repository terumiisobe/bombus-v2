package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CountColmeiasQuery
import com.bombus.colmeia.application.port.inbound.CountColmeiasUseCase
import com.bombus.colmeia.application.port.inbound.CountDimension
import com.bombus.colmeia.application.port.outbound.ColmeiaCountPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCount
import com.bombus.colmeia.domain.ColmeiaCountFilter
import com.bombus.colmeia.domain.SpeciesCount
import com.bombus.colmeia.domain.StatusCount
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CountColmeiasService(
    private val countPort: ColmeiaCountPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
) : CountColmeiasUseCase {

    override fun count(query: CountColmeiasQuery): ColmeiaCount =
        if (query.groupBy.isEmpty()) plainCount(query) else breakdown(query)

    private fun plainCount(query: CountColmeiasQuery): ColmeiaCount =
        ColmeiaCount(total = countPort.countByOwner(query.userId, toFilter(query)))

    // A breakdown shows every subgroup, so it applies no default exclusion; only the
    // explicit species/status filters from the query narrow the population.
    private fun breakdown(query: CountColmeiasQuery): ColmeiaCount {
        val filter = ColmeiaCountFilter(speciesId = query.speciesId, includeStatusId = query.statusId)
        val perSpecies: List<SpeciesCount>? =
            if (CountDimension.SPECIES in query.groupBy) countPort.breakdownBySpecies(query.userId, filter) else null
        val perStatus: List<StatusCount>? =
            if (CountDimension.STATUS in query.groupBy) countPort.breakdownByStatus(query.userId, filter) else null
        val total = perSpecies?.sumOf { it.count } ?: perStatus?.sumOf { it.count } ?: 0L
        return ColmeiaCount(total = total, perSpecies = perSpecies, perStatus = perStatus)
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
