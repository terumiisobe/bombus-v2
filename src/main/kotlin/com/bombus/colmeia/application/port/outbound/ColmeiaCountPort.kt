package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.ColmeiaCountFilter
import com.bombus.colmeia.domain.SpeciesCount
import com.bombus.colmeia.domain.StatusCount

/**
 * Hive counts across every meliponário [userId] is a member of.
 * "Owner" means member. meliponario.owner_id grants nothing.
 */
interface ColmeiaCountPort {

    fun countByOwner(userId: Long, filter: ColmeiaCountFilter): Long

    fun breakdownBySpecies(userId: Long, filter: ColmeiaCountFilter): List<SpeciesCount>

    fun breakdownByStatus(userId: Long, filter: ColmeiaCountFilter): List<StatusCount>
}
