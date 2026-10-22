package com.bombus.colmeia.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * Physical presence derived from the latest [localizacao] row (append-only).
 *
 * Orthogonal to business disposition (`vendida` / `perdida`):
 * - [LivePin] — on-site; disposed codes stay held until a clear event.
 * - [Cleared] — visit-2 clear is current; disposed codes soft-released.
 * - [NeverPinned] — no rows; disposed codes soft-released at mark.
 */
sealed interface HiveLocationPresence {

    data class LivePin(
        val latitude: BigDecimal,
        val longitude: BigDecimal,
        val recordedAt: Instant,
    ) : HiveLocationPresence

    data class Cleared(
        val recordedAt: Instant,
    ) : HiveLocationPresence

    data object NeverPinned : HiveLocationPresence

    /** True only for a current GPS pin (not clear, not absent). */
    val hasLivePin: Boolean
        get() = this is LivePin
}

/**
 * Whether a hive with this disposition + presence still blocks soft reuse of its code.
 * Active (non-disposition) always holds; disposition holds only while [presence] is a live pin.
 */
fun holdsCodeForReuse(isDispositionStatus: Boolean, presence: HiveLocationPresence): Boolean =
    !isDispositionStatus || presence.hasLivePin

/**
 * Whether a hive appears in `GET /v1/meliponarios/{id}` `hives[]`.
 * Active hives always included (even never-pinned → null lat/lng).
 * Disposed (`vendida`/`perdida`) only while [presence] is a live pin (onsite awaiting pickup);
 * cleared or never-pinned disposed are omitted.
 */
fun includeOnMeliponarioMap(isDispositionStatus: Boolean, presence: HiveLocationPresence): Boolean =
    !isDispositionStatus || presence.hasLivePin

/**
 * Map the latest location reading (or none) to presence.
 * Callers must pass the current row only (latest by recorded_at, id).
 */
fun presenceFromLatest(reading: LocalizacaoReading?): HiveLocationPresence = when {
    reading == null -> HiveLocationPresence.NeverPinned
    reading.isClearEvent -> HiveLocationPresence.Cleared(reading.recordedAt)
    else -> HiveLocationPresence.LivePin(
        latitude = requireNotNull(reading.latitude) { "live pin requires latitude" },
        longitude = requireNotNull(reading.longitude) { "live pin requires longitude" },
        recordedAt = reading.recordedAt,
    )
}
