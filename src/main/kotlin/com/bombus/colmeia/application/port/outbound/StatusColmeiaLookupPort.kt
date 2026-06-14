package com.bombus.colmeia.application.port.outbound

interface StatusColmeiaLookupPort {

    fun findIdByName(name: String): Long?
}
