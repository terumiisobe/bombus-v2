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
    private val activeCodeResolver: ColmeiaActiveCodeResolver,
    private val codeAvailability: ColmeiaCodeAvailability,
    private val clock: Clock,
) : UpdateColmeiaUseCase {

    @Transactional
    override fun update(command: UpdateColmeiaCommand): ColmeiaSummary {
        val existing = activeCodeResolver.requireExactlyOneActive(command.userId, command.code)
        val statusRef = vocabularyPort.listStatuses().find { it.id == command.statusId }
            ?: throw ColmeiaCommandError.UnknownStatus()
        if (command.statusId == existing.statusId) return existing

        // Staying active: code must not be held by another active hive.
        // Codes only on perdida/vendida do not block (soft uniqueness).
        if (!properties.releasesCode(statusRef.name)) {
            codeAvailability.assertAvailable(
                meliponarioId = existing.meliponarioId,
                code = existing.code,
                exceptColmeiaId = existing.id,
            )
        }

        return ownedColmeiaPort.appendStatus(existing.id, command.statusId, clock.instant())
            ?: throw ColmeiaCommandError.ColmeiaNotFound()
    }
}
