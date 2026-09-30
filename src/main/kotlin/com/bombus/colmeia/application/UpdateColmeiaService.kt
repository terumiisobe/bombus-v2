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
    private val clock: Clock,
) : UpdateColmeiaUseCase {

    @Transactional
    override fun update(command: UpdateColmeiaCommand): ColmeiaSummary {
        val existing = resolveOwned(command.userId, command.colmeiaId, command.code, command.meliponarioId)
        command.speciesId?.let { speciesId ->
            if (vocabularyPort.listSpecies().none { it.id == speciesId }) {
                throw ColmeiaCommandError.UnknownSpecies()
            }
        }
        command.statusId?.let { statusId ->
            if (vocabularyPort.listStatuses().none { it.id == statusId }) {
                throw ColmeiaCommandError.UnknownStatus()
            }
        }

        var current = existing
        if (command.speciesId != null || command.startDate != null) {
            current = ownedColmeiaPort.update(
                colmeiaId = existing.id,
                speciesId = command.speciesId,
                startDate = command.startDate,
            ) ?: throw ColmeiaCommandError.ColmeiaNotFound()
        }
        if (command.statusId != null && command.statusId != existing.statusId) {
            current = ownedColmeiaPort.appendStatus(existing.id, command.statusId, clock.instant())
                ?: throw ColmeiaCommandError.ColmeiaNotFound()
        }
        return current
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
