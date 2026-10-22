package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import org.springframework.stereotype.Component

/**
 * Soft uniqueness for hive codes within a meliponário.
 *
 * Disposition statuses (`perdida` / `vendida`) are passed as [OwnedColmeiaPort.isCodeTaken]
 * ignore ids: those rows free the code only when they have **no live pin**
 * (never pinned, or latest localizacao is a clear event). Pinned disposed hives still block.
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
        val dispositionIds = properties.defaultExcludedStatuses
            .mapNotNull { statusLookupPort.findIdByName(it) }
            .toSet()
        if (ownedColmeiaPort.isCodeTaken(
                meliponarioId = meliponarioId,
                code = code,
                exceptColmeiaId = exceptColmeiaId,
                ignoreStatusIds = dispositionIds,
            )
        ) {
            throw ColmeiaCommandError.CodeTaken()
        }
    }
}
