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

@Service
class CreateColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
    private val vocabularyPort: ColmeiaVocabularyPort,
    private val statusLookupPort: StatusColmeiaLookupPort,
    private val properties: ColmeiaCountProperties,
) : CreateColmeiaUseCase {

    @Transactional
    override fun create(command: CreateColmeiaCommand): ColmeiaSummary {
        val meliponarioId = resolveMeliponario(command.userId)
        requireKnownSpecies(command.speciesId)

        val releasingIds = properties.defaultExcludedStatuses
            .mapNotNull { statusLookupPort.findIdByName(it) }
            .toSet()

        val code = command.code
        if (code != null) {
            if (code < 1) throw ColmeiaCommandError.CodeTaken()
            if (ownedColmeiaPort.isCodeTaken(meliponarioId, code, ignoreStatusIds = releasingIds)) {
                throw ColmeiaCommandError.CodeTaken()
            }
        }

        val statusId = command.statusId
            ?: statusLookupPort.findIdByName(DEFAULT_CREATE_STATUS)
            ?: throw ColmeiaCommandError.UnknownStatus()
        requireKnownStatus(statusId)

        val created = ownedColmeiaPort.insert(
            code = code,
            speciesId = command.speciesId,
            meliponarioId = meliponarioId,
            startDate = command.startDate,
            initialStatusId = statusId,
        )

        val statusName = vocabularyPort.listStatuses().find { it.id == statusId }?.name
        if (statusName != null && properties.releasesCode(statusName)) {
            return ownedColmeiaPort.clearCode(created.id) ?: created
        }
        return created
    }

    private fun resolveMeliponario(userId: Long): Long {
        val owned = ownedColmeiaPort.listMeliponarioIdsByOwner(userId)
        if (owned.isEmpty()) throw ColmeiaCommandError.NoMeliponario()
        return owned.first()
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
        const val DEFAULT_CREATE_STATUS = "desenvolvendo"
    }
}
