package com.bombus.colmeia.domain

import java.time.Instant

/**
 * One historico / visit row for chat and read use cases.
 * Exposes date ([recordedAt]), status name, and optional note — no source or visitedAt.
 */
data class ColmeiaStatusHistoryEntry(
    val recordedAt: Instant,
    val statusName: String,
    val note: String?,
)
