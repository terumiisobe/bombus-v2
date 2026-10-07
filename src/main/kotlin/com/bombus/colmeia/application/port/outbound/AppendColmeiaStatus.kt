package com.bombus.colmeia.application.port.outbound

import java.time.Instant

/**
 * Payload for appending a status-history (visit/observation) row.
 * [recordedAt] is the sole timeline timestamp; optional fields enrich the record.
 */
data class AppendColmeiaStatus(
    val colmeiaId: Long,
    val statusId: Long,
    val recordedAt: Instant,
    val recordedByUserId: Long? = null,
    val note: String? = null,
    val source: String? = null,
)
