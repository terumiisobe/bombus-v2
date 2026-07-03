package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.ListColmeiaVocabularyUseCase
import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.domain.ColmeiaVocabulary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ListColmeiaVocabularyService(
    private val vocabularyPort: ColmeiaVocabularyPort,
) : ListColmeiaVocabularyUseCase {

    override fun list(): ColmeiaVocabulary =
        ColmeiaVocabulary(
            species = vocabularyPort.listSpecies(),
            statuses = vocabularyPort.listStatuses(),
        )
}
