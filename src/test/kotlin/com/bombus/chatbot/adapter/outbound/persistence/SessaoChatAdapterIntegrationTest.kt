package com.bombus.chatbot.adapter.outbound.persistence

import com.bombus.chatbot.application.port.outbound.SaveSessionCommand
import com.bombus.chatbot.domain.ConversationContext
import com.bombus.chatbot.domain.ConversationRole
import com.bombus.chatbot.domain.ConversationTurn
import com.bombus.config.BombusApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit

@Testcontainers
@Transactional
@SpringBootTest(
    classes = [BombusApplication::class],
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "twilio.auth-token=test-auth-token",
        "twilio.public-base-url=https://example.test",
        "openai.api-key=test-openai-key",
    ],
)
class SessaoChatAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: SessaoChatAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val now: Instant = Instant.parse("2026-07-03T12:00:00Z").truncatedTo(ChronoUnit.MILLIS)

    @BeforeEach
    fun seed() {
        jdbcTemplate.update("INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)", USER_ID, "o@x.test", "h")
        jdbcTemplate.update(
            "INSERT INTO usuario_whatsapp (id, usuario_id, phone_number, active) VALUES (?, ?, ?, TRUE)",
            WHATSAPP_USER_ID,
            USER_ID,
            "+5511999999999",
        )
    }

    @Test
    fun `returns null when no session exists`() {
        assertThat(adapter.load(WHATSAPP_USER_ID)).isNull()
    }

    @Test
    fun `saves then loads the conversation context and expiry`() {
        val context = ConversationContext(
            listOf(
                ConversationTurn(ConversationRole.USER, "quantas colmeias?"),
                ConversationTurn(ConversationRole.ASSISTANT, "Você tem 5 colmeias."),
            ),
        )
        adapter.save(SaveSessionCommand(WHATSAPP_USER_ID, context, lastMessageAt = now, expiresAt = now.plusSeconds(900)))

        val loaded = adapter.load(WHATSAPP_USER_ID)

        assertThat(loaded).isNotNull
        assertThat(loaded!!.context).isEqualTo(context)
        assertThat(loaded.expiresAt).isEqualTo(now.plusSeconds(900))
    }

    @Test
    fun `upsert replaces the existing row for the user`() {
        adapter.save(
            SaveSessionCommand(
                WHATSAPP_USER_ID,
                ConversationContext(listOf(ConversationTurn(ConversationRole.USER, "primeiro"))),
                lastMessageAt = now,
                expiresAt = now.plusSeconds(900),
            ),
        )
        adapter.save(
            SaveSessionCommand(
                WHATSAPP_USER_ID,
                ConversationContext(listOf(ConversationTurn(ConversationRole.USER, "segundo"))),
                lastMessageAt = now.plusSeconds(10),
                expiresAt = now.plusSeconds(910),
            ),
        )

        val loaded = adapter.load(WHATSAPP_USER_ID)
        assertThat(loaded!!.context.recentTurns.map { it.text }).containsExactly("segundo")

        val rows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sessao_chat WHERE usuario_whatsapp_id = ?",
            Long::class.java,
            WHATSAPP_USER_ID,
        )
        assertThat(rows).isEqualTo(1L)
    }

    companion object {
        private const val USER_ID = 1L
        private const val WHATSAPP_USER_ID = 7L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
    }
}
