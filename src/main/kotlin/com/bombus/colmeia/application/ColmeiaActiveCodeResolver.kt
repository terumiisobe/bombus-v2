package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Component

/**
 * Resolves a hive by code for update/delete: the code must identify exactly one
 * **active** hive. Rows in code-releasing statuses (perdida/vendida) are ignored
 * and never mutated by those operations.
 */
@Component
class ColmeiaActiveCodeResolver(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val properties: ColmeiaCountProperties,
) {

    fun requireExactlyOneActive(userId: Long, code: Int): ColmeiaSummary {
        val active = ownedColmeiaPort.findByCodeForOwner(userId, code)
            .filterNot { properties.releasesCode(it.statusName) }
        return when (active.size) {
            1 -> active.first()
            0 -> throw ColmeiaCommandError.ColmeiaNotFound()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
    }
}
