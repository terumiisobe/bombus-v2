package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import org.springframework.stereotype.Component

/**
 * Soft uniqueness for hive codes: a code held only by [perdida]/[vendida] hives
 * is still available for create / for an active hive (including revive from those statuses).
 */
@Component
class ColmeiaCodeAvailability(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
) {

    fun assertAvailable(meliponarioId: Long, code: Int?, exceptColmeiaId: Long? = null) {
        if (code == null) return
        if (code < 1) throw ColmeiaCommandError.CodeTaken()
        val releasingIds = properties.defaultExcludedStatuses
            .mapNotNull { statusLookupPort.findIdByName(it) }
            .toSet()
        if (ownedColmeiaPort.isCodeTaken(
                meliponarioId = meliponarioId,
                code = code,
                exceptColmeiaId = exceptColmeiaId,
                ignoreStatusIds = releasingIds,
            )
        ) {
            throw ColmeiaCommandError.CodeTaken()
        }
    }
}
