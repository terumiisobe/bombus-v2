package com.bombus.chatbot.application

import com.bombus.colmeia.domain.ColmeiaStatusHistoryEntry
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class FieldVisitReplyFormatter(
    zone: ZoneId = ZoneId.of("America/Sao_Paulo"),
) {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zone)

    fun format(entry: ColmeiaStatusHistoryEntry): String {
        val line = "${formatter.format(entry.recordedAt)} — ${entry.statusName}"
        val note = entry.note?.takeIf { it.isNotBlank() } ?: return line
        return "$line\nnota: $note"
    }

    fun formatDate(entry: ColmeiaStatusHistoryEntry): String = formatter.format(entry.recordedAt)

    fun emptyHistory(code: Int): String = "Nenhuma visita registrada para o código $code."
}
