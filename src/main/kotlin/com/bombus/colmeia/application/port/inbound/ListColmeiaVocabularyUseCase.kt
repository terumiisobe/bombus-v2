package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaVocabulary

interface ListColmeiaVocabularyUseCase {

    fun list(): ColmeiaVocabulary
}
