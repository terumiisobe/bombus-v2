package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.ColmeiaCountFilter

interface ColmeiaCountPort {

    fun countByOwner(userId: Long, filter: ColmeiaCountFilter): Long
}
