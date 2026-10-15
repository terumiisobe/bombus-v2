package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.MeliponarioMapView

interface GetMeliponarioMapUseCase {
    fun get(query: GetMeliponarioMapQuery): MeliponarioMapView
}

data class GetMeliponarioMapQuery(
    val userId: Long,
    val meliponarioId: Long,
)
