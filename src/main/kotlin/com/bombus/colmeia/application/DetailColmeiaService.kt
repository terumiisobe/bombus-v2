package com.bombus.colmeia.application

import com.bombus.colmeia.application.port.inbound.DetailColmeiaQuery
import com.bombus.colmeia.application.port.inbound.DetailColmeiaUseCase
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaCommandError
import com.bombus.colmeia.domain.ColmeiaDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class DetailColmeiaService(
    private val ownedColmeiaPort: OwnedColmeiaPort,
) : DetailColmeiaUseCase {

    override fun detail(query: DetailColmeiaQuery): ColmeiaDetail {
        val hive = ownedColmeiaPort.findByIdForOwner(query.userId, query.colmeiaId)
            ?: throw ColmeiaCommandError.ColmeiaNotFound()
        val lastVisit = ownedColmeiaPort.listStatusHistory(
            userId = query.userId,
            colmeiaId = query.colmeiaId,
            limit = 1,
        ).firstOrNull()
        return ColmeiaDetail(
            code = hive.code,
            speciesCommonName = hive.speciesCommonName,
            statusName = hive.statusName,
            lastVisit = lastVisit,
        )
    }
}
