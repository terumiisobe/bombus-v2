package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusCommand
import com.bombus.colmeia.application.port.inbound.RecordColmeiaStatusUseCase
import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class RecordColmeiaStatusService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val vocabularyPort: ColmeiaVocabularyPort,
    private val properties: ColmeiaCountProperties,
    private val codeAvailability: ColmeiaCodeAvailability,
    private val clock: Clock,
) : RecordColmeiaStatusUseCase {

    @Transactional
    override fun record(command: RecordColmeiaStatusCommand): ColmeiaSummary {
        val existing = ownedColmeiaPort.findByIdForOwner(command.userId, command.colmeiaId)
            ?: throw ColmeiaCommandError.ColmeiaNotFound()
        val statusRef = vocabularyPort.listStatuses().find { it.id == command.statusId }
            ?: throw ColmeiaCommandError.UnknownStatus()
        val note = normalizeNote(command.note)

        if (!properties.releasesCode(statusRef.name) && existing.code != null) {
            codeAvailability.assertAvailable(
                meliponarioId = existing.meliponarioId,
                code = existing.code,
                exceptColmeiaId = existing.id,
            )
        }

        return ownedColmeiaPort.appendStatus(
            AppendColmeiaStatus(
                colmeiaId = existing.id,
                statusId = command.statusId,
                recordedAt = clock.instant(),
                recordedByUserId = command.userId,
                note = note,
            ),
        ) ?: throw ColmeiaCommandError.ColmeiaNotFound()
    }

    private fun normalizeNote(note: String?): String? {
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(trimmed.length <= MAX_NOTE_LENGTH) {
            "note must be at most $MAX_NOTE_LENGTH characters"
        }
        return trimmed
    }

    companion object {
        const val MAX_NOTE_LENGTH = 280
    }
}
