package com.bombus.colmeia.application.port.outbound

import com.bombus.colmeia.domain.MeliponarioMapView

/**
 * Loads one meliponário plus its map hives (latest status + latest location) in a
 * membership-scoped read. Removed disposed hives (no live pin) are omitted; active
 * unpinned hives remain. Returns null when the yard does not exist or [userId] is not a member.
 */
interface LoadMeliponarioMapPort {
    fun loadForMember(userId: Long, meliponarioId: Long): MeliponarioMapView?
}
