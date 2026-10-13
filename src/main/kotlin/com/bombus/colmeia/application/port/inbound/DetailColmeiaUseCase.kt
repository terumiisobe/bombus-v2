package com.bombus.colmeia.application.port.inbound

import com.bombus.colmeia.domain.ColmeiaDetail

interface DetailColmeiaUseCase {
    fun detail(query: DetailColmeiaQuery): ColmeiaDetail
}

data class DetailColmeiaQuery(
    val userId: Long,
    val colmeiaId: Long,
)
