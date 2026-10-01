package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.DeleteColmeiaCommand
import com.bombus.colmeia.application.port.inbound.DeleteColmeiaUseCase
import com.bombus.colmeia.application.port.inbound.DeletedColmeia
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
) : DeleteColmeiaUseCase {

    @Transactional
    override fun delete(command: DeleteColmeiaCommand): DeletedColmeia {
        val matches = ownedColmeiaPort.findByCodeForOwner(command.userId, command.code)
        val existing = when (matches.size) {
            0 -> throw ColmeiaCommandError.ColmeiaNotFound()
            1 -> matches.first()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
        val deleted = ownedColmeiaPort.deleteByIdForOwner(command.userId, existing.id)
        if (!deleted) throw ColmeiaCommandError.ColmeiaNotFound()
        return DeletedColmeia(
            code = existing.code,
            speciesCommonName = existing.speciesCommonName,
            statusName = existing.statusName,
        )
    }
}
