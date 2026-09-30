package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.SoftDeleteColmeiaUseCase
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class SoftDeleteColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
    private val clock: Clock,
) : SoftDeleteColmeiaUseCase {

    @Transactional
    override fun softDelete(command: SoftDeleteColmeiaCommand): ColmeiaSummary {
        val existing = resolveOwned(command.userId, command.colmeiaId, command.code, command.meliponarioId)
        val perdidaId = statusLookupPort.findIdByName(properties.defaultExcludedStatus)
            ?: throw ColmeiaCommandError.UnknownStatus()
        // Idempotent: already soft-deleted → same end state, no extra history row.
        if (existing.statusId == perdidaId) return existing
        return ownedColmeiaPort.appendStatus(existing.id, perdidaId, clock.instant())
            ?: throw ColmeiaCommandError.ColmeiaNotFound()
    }

    private fun resolveOwned(
        userId: Long,
        colmeiaId: Long?,
        code: Int?,
        meliponarioId: Long?,
    ): ColmeiaSummary {
        if (colmeiaId != null) {
            return ownedColmeiaPort.findByIdForOwner(userId, colmeiaId)
                ?: throw ColmeiaCommandError.ColmeiaNotFound()
        }
        if (code == null) throw ColmeiaCommandError.ColmeiaNotFound()
        val matches = ownedColmeiaPort.findByCodeForOwner(userId, code, meliponarioId)
        return when (matches.size) {
            0 -> throw ColmeiaCommandError.ColmeiaNotFound()
            1 -> matches.first()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
    }
}
