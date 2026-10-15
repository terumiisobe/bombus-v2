package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapQuery
import com.bombus.colmeia.application.port.inbound.GetMeliponarioMapUseCase
import com.bombus.colmeia.application.port.outbound.LoadMeliponarioMapPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.MeliponarioMapView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GetMeliponarioMapService(
    private val loadMeliponarioMapPort: LoadMeliponarioMapPort,
) : GetMeliponarioMapUseCase {

    override fun get(query: GetMeliponarioMapQuery): MeliponarioMapView =
        loadMeliponarioMapPort.loadForMember(query.userId, query.meliponarioId)
            ?: throw ColmeiaCommandError.MeliponarioNotFound()
}
