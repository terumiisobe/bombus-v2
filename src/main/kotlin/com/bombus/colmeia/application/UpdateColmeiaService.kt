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
        if (vocabularyPort.listStatuses().none { it.id == command.statusId }) {
            throw ColmeiaCommandError.UnknownStatus()
        }
        if (command.statusId == existing.statusId) return existing
        return ownedColmeiaPort.appendStatus(existing.id, command.statusId, clock.instant())
            ?: throw ColmeiaCommandError.ColmeiaNotFound()
    }

    /**
     * Prefer an active (non-releasing) hive when a code was reused after perdida/vendida.
     * If none are active, fall back to a single releasing-status match so that hive can still be updated.
     */
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
