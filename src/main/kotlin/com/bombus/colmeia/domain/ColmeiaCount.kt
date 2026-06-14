package com.bombus.colmeia.domain

data class ColmeiaCount(
    val total: Long,
    val perSpecies: List<SpeciesCount>? = null,
    val perStatus: List<StatusCount>? = null,
)

data class SpeciesCount(
    val speciesId: Long,
    val abbreviation: String,
    val commonName: String,
    val count: Long,
)

data class StatusCount(
    val statusId: Long?,
    val statusName: String?,
    val count: Long,
)
