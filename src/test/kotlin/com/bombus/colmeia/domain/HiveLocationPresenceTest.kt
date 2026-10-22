package com.bombus.colmeia.domain

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiveLocationPresenceTest {

    private val at = Instant.parse("2026-10-15T12:00:00Z")

    @Test
    fun `live pin from reading holds code when disposed`() {
        val reading = LocalizacaoReading(
            id = 1L,
            colmeiaId = 10L,
            latitude = BigDecimal("-23.5"),
            longitude = BigDecimal("-46.6"),
            recordedAt = at,
        )
        val presence = presenceFromLatest(reading)
        assertTrue(presence.hasLivePin)
        assertTrue(holdsCodeForReuse(isDispositionStatus = true, presence = presence))
        assertTrue(holdsCodeForReuse(isDispositionStatus = false, presence = presence))
    }

    @Test
    fun `clear event frees code when disposed`() {
        val reading = LocalizacaoReading(
            id = 2L,
            colmeiaId = 10L,
            latitude = null,
            longitude = null,
            recordedAt = at,
        )
        assertTrue(reading.isClearEvent)
        assertFalse(reading.isLivePin)
        val presence = presenceFromLatest(reading)
        assertEquals(HiveLocationPresence.Cleared(at), presence)
        assertFalse(holdsCodeForReuse(isDispositionStatus = true, presence = presence))
        assertTrue(holdsCodeForReuse(isDispositionStatus = false, presence = presence))
    }

    @Test
    fun `never pinned frees code when disposed`() {
        val presence = presenceFromLatest(null)
        assertEquals(HiveLocationPresence.NeverPinned, presence)
        assertFalse(presence.hasLivePin)
        assertFalse(holdsCodeForReuse(isDispositionStatus = true, presence = presence))
        assertTrue(holdsCodeForReuse(isDispositionStatus = false, presence = presence))
    }

    @Test
    fun `map includes disposed only with live pin`() {
        val pinned = presenceFromLatest(
            LocalizacaoReading(1L, 10L, BigDecimal("-23.5"), BigDecimal("-46.6"), at),
        )
        val cleared = presenceFromLatest(
            LocalizacaoReading(2L, 10L, null, null, at),
        )
        val neverPinned = presenceFromLatest(null)

        assertTrue(includeOnMeliponarioMap(isDispositionStatus = true, presence = pinned))
        assertFalse(includeOnMeliponarioMap(isDispositionStatus = true, presence = cleared))
        assertFalse(includeOnMeliponarioMap(isDispositionStatus = true, presence = neverPinned))
        assertTrue(includeOnMeliponarioMap(isDispositionStatus = false, presence = neverPinned))
    }
}
