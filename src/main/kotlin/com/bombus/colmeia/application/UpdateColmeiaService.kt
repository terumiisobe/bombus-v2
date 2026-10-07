package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.UpdateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.UpdateColmeiaUseCase
import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class UpdateColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val vocabularyPort: ColmeiaVocabularyPort,
    private val properties: ColmeiaCountProperties,
    private val clock: Clock,
) : UpdateColmeiaUseCase {

    @Transactional
    override fun update(command: UpdateColmeiaCommand): ColmeiaSummary {
        val existing = resolveByCode(command.userId, command.code)
        val statusRef = vocabularyPort.listStatuses().find { it.id == command.statusId }
            ?: throw ColmeiaCommandError.UnknownStatus()
        if (command.statusId == existing.statusId) return existing
        val updated = ownedColmeiaPort.appendStatus(existing.id, command.statusId, clock.instant())
            ?: throw ColmeiaCommandError.ColmeiaNotFound()
        if (properties.releasesCode(statusRef.name)) {
            return ownedColmeiaPort.clearCode(updated.id) ?: updated
        }
        return updated
    }

    private fun resolveByCode(userId: Long, code: Int): ColmeiaSummary {
        val matches = ownedColmeiaPort.findByCodeForOwner(userId, code)
        return when (matches.size) {
            0 -> throw ColmeiaCommandError.ColmeiaNotFound()
            1 -> matches.first()
            else -> throw ColmeiaCommandError.AmbiguousCode()
        }
    }
}
