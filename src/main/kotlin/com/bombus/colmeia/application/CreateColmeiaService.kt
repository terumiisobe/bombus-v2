package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.CreateColmeiaCommand
import com.bombus.colmeia.application.port.inbound.CreateColmeiaUseCase
import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class CreateColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val vocabularyPort: ColmeiaVocabularyPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
    private val clock: Clock,
) : CreateColmeiaUseCase {

    @Transactional
    override fun create(command: CreateColmeiaCommand): ColmeiaSummary {
        val meliponarioId = resolveMeliponario(command.userId, command.meliponarioId)
        requireKnownSpecies(command.speciesId)

        val excludeStatusId = statusLookupPort.findIdByName(properties.defaultExcludedStatus)
        val code = when (val requested = command.code) {
            null -> ownedColmeiaPort.nextFreeCode(meliponarioId, excludeStatusId)
            else -> {
                if (requested < 1) throw ColmeiaCommandError.CodeTaken()
                if (ownedColmeiaPort.isCodeTaken(meliponarioId, requested, excludeStatusId)) {
                    throw ColmeiaCommandError.CodeTaken()
                }
                requested
            }
        }

        val statusId = command.statusId
            ?: statusLookupPort.findIdByName(DEFAULT_CREATE_STATUS)
            ?: throw ColmeiaCommandError.UnknownStatus()
        requireKnownStatus(statusId)

        val startDate = command.startDate ?: clock.instant()
        return ownedColmeiaPort.insert(
            code = code,
            speciesId = command.speciesId,
            meliponarioId = meliponarioId,
            startDate = startDate,
            initialStatusId = statusId,
        )
    }

    private fun resolveMeliponario(userId: Long, requested: Long?): Long {
        val owned = ownedColmeiaPort.listMeliponarioIdsByOwner(userId)
        if (owned.isEmpty()) throw ColmeiaCommandError.NoMeliponario()
        if (requested == null) return owned.first()
        if (requested !in owned) throw ColmeiaCommandError.MeliponarioNotOwned()
        return requested
    }

    private fun requireKnownSpecies(speciesId: Long) {
        if (vocabularyPort.listSpecies().none { it.id == speciesId }) {
            throw ColmeiaCommandError.UnknownSpecies()
        }
    }

    private fun requireKnownStatus(statusId: Long) {
        if (vocabularyPort.listStatuses().none { it.id == statusId }) {
            throw ColmeiaCommandError.UnknownStatus()
        }
    }

    companion object {
        const val DEFAULT_CREATE_STATUS = "estavel"
    }
}
