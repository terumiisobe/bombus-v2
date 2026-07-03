package com.bombus.colmeia.domain

data class ColmeiaVocabulary(
    val species: List<SpeciesRef>,
    val statuses: List<StatusRef>,
)

data class SpeciesRef(
    val id: Long,
    val abbreviation: String,
    val commonName: String,
    val scientificName: String,
)

data class StatusRef(
    val id: Long,
    val name: String,
)
