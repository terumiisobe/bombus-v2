package com.bombus.chatbot.application

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formats field-visit history for WhatsApp: date only (America/Sao_Paulo), status,
 * optional note in parentheses — no time, no "nota:" label.
 */
class FieldVisitReplyFormatter(
    zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)

    fun format(entry: ColmeiaStatusHistoryEntry): String {
        val line = "${formatDate(entry)} · ${entry.statusName}"
        val note = entry.note?.takeIf { it.isNotBlank() } ?: return line
        return "$line ($note)"
    }

    fun formatDate(entry: ColmeiaStatusHistoryEntry): String = dateFormatter.format(entry.recordedAt)

    fun emptyHistory(code: Int): String = "Nenhuma visita registrada para o código $code."
}
