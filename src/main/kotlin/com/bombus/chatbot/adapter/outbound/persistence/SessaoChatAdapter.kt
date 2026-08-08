package com.bombus.chatbot.adapter.outbound.persistence

import com.bombus.chatbot.application.port.outbound.ChatSessionPort
import com.bombus.chatbot.application.port.outbound.SaveSessionCommand
import com.bombus.chatbot.application.port.outbound.StoredSession
import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.ConversationRole
import com.bombus.chatbot.domain.ConversationTurn
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * Driven adapter over the sessao_chat table. Persists the bounded conversation context as
 * JSONB; a single active row per user (uq_sessao_chat_usuario_whatsapp) is upserted on each
 * turn. Persistence DTOs stay inside the adapter and are mapped to the domain at the boundary.
 */
@Component
class SessaoChatAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : ChatSessionPort {

    override fun load(whatsappUserId: Long): StoredSession? {
        val params = MapSqlParameterSource("whatsappUserId", whatsappUserId)
        return jdbc.query(LOAD_SQL, params, SESSION_MAPPER).firstOrNull()
    }

    override fun save(command: SaveSessionCommand) {
        val params = MapSqlParameterSource()
            .addValue("whatsappUserId", command.whatsappUserId)
            .addValue("context", serialize(command.context))
            .addValue("lastMessageAt", Timestamp.from(command.lastMessageAt))
            .addValue("expiresAt", Timestamp.from(command.expiresAt))
        jdbc.update(SAVE_SQL, params)
    }

    private fun serialize(context: ConversationContext): String =
        objectMapper.writeValueAsString(context.recentTurns.map { TurnDto(it.role.name, it.text) })

    private fun deserialize(json: String): ConversationContext {
        val turns = objectMapper.readValue<List<TurnDto>>(json)
            .map { ConversationTurn(ConversationRole.valueOf(it.role), it.text) }
        return ConversationContext(turns)
    }

    private data class TurnDto(val role: String = "", val text: String = "")

    private val SESSION_MAPPER = RowMapper { rs, _ ->
        StoredSession(
            context = deserialize(rs.getString("conversation_context")),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
        )
    }

    private companion object {
        val LOAD_SQL = """
            SELECT conversation_context, expires_at
            FROM sessao_chat
            WHERE usuario_whatsapp_id = :whatsappUserId
        """.trimIndent()

        val SAVE_SQL = """
            INSERT INTO sessao_chat (usuario_whatsapp_id, conversation_context, last_message_at, expires_at)
            VALUES (:whatsappUserId, CAST(:context AS jsonb), :lastMessageAt, :expiresAt)
            ON CONFLICT (usuario_whatsapp_id) DO UPDATE SET
                conversation_context = EXCLUDED.conversation_context,
                last_message_at = EXCLUDED.last_message_at,
                expires_at = EXCLUDED.expires_at
        """.trimIndent()
    }
}
