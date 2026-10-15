package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.MeliponarioMapView

/**
 * Loads one meliponário plus its hives (latest status + latest location) in a membership-scoped read.
 * Returns null when the yard does not exist or [userId] is not a member.
 */
interface LoadMeliponarioMapPort {
    fun loadForMember(userId: Long, meliponarioId: Long): MeliponarioMapView?
}
