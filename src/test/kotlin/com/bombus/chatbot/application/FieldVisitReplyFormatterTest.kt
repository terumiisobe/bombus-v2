package com.bombus.chatbot.application

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldVisitReplyFormatterTest {

    private val formatter = FieldVisitReplyFormatter(ZoneId.of("America/Sao_Paulo"))

    @Test
    fun `formats the instant in Sao Paulo and appends a note`() {
        val line = formatter.format(
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-08T15:26:10Z"),
                statusName = "estavel",
                note = "colônia forte",
            ),
        )

        assertEquals("08/10/2026 12:26 — estavel\nnota: colônia forte", line)
    }

    @Test
    fun `omits the note line when the note is blank`() {
        val line = formatter.format(
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-01T12:00:00Z"),
                statusName = "estavel",
                note = null,
            ),
        )

        assertEquals("01/10/2026 09:00 — estavel", line)
    }

    @Test
    fun `empty history names the code`() {
        assertEquals("Nenhuma visita registrada para o código 7.", formatter.emptyHistory(7))
    }
}
