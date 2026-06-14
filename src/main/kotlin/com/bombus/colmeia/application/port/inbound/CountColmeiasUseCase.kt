package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaCount

interface CountColmeiasUseCase {

    fun count(query: CountColmeiasQuery): ColmeiaCount
}

data class CountColmeiasQuery(
    val userId: Long,
    val speciesId: Long? = null,
    val statusId: Long? = null,
    val groupBy: Set<CountDimension> = emptySet(),
)

enum class CountDimension { SPECIES, STATUS }
