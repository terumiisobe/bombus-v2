package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef

interface ColmeiaVocabularyPort {

    fun listSpecies(): List<SpeciesRef>

    fun listStatuses(): List<StatusRef>
}
