package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.DeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.DeletedColmeia
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val properties: ColmeiaCountProperties,
) : DeleteColmeiaUseCase {

    @Transactional
    override fun delete(command: DeleteColmeiaCommand): DeletedColmeia {
        val existing = resolveByCode(command.userId, command.code)
        val deleted = ownedColmeiaPort.deleteByIdForOwner(command.userId, existing.id)
        if (!deleted) throw ColmeiaCommandError.ColmeiaNotFound()
        return DeletedColmeia(
            code = existing.code,
            speciesCommonName = existing.speciesCommonName,
            statusName = existing.statusName,
        )
    }

    /** Same prefer-active rule as update — see [UpdateColmeiaService]. */
    private fun resolveByCode(userId: Long, code: Int): ColmeiaSummary {
        val matches = ownedColmeiaPort.findByCodeForOwner(userId, code)
        val active = matches.filterNot { properties.releasesCode(it.statusName) }
        return when {
            active.size == 1 -> active.first()
            active.isEmpty() && matches.size == 1 -> matches.first()
            active.isEmpty() && matches.isEmpty() -> throw ColmeiaCommandError.ColmeiaNotFound()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
    }
}
