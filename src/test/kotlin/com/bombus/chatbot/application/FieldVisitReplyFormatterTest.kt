package com.bombus.chatbot.application

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldVisitReplyFormatterTest {

    private val formatter = FieldVisitReplyFormatter(ZoneId.of("America/Sao_Paulo"))

    @Test
    fun `formats date only with status and note in parentheses`() {
        val line = formatter.format(
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-13T15:26:10Z"),
                statusName = "estavel",
                note = "entrada limpa",
            ),
        )

        assertEquals("2026-10-13 · estavel (entrada limpa)", line)
    }

    @Test
    fun `omits parentheses when the note is blank`() {
        val line = formatter.format(
            ColmeiaStatusHistoryEntry(
                recordedAt = Instant.parse("2026-10-01T12:00:00Z"),
                statusName = "estavel",
                note = null,
            ),
        )

        assertEquals("2026-10-01 · estavel", line)
    }

    @Test
    fun `formatDate is calendar date only without time`() {
        assertEquals(
            "2026-10-08",
            formatter.formatDate(
                ColmeiaStatusHistoryEntry(
                    recordedAt = Instant.parse("2026-10-08T15:26:10Z"),
                    statusName = "estavel",
                    note = null,
                ),
            ),
        )
    }

    @Test
    fun `empty history names the code`() {
        assertEquals("Nenhuma visita registrada para o código 7.", formatter.emptyHistory(7))
    }
}
