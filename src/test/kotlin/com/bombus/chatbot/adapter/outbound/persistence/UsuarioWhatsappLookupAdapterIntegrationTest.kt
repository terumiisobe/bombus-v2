package com.bombus.chatbot.adapter.outbound.persistence

import com.bombus.chatbot.domain.PhoneNumber
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

@Testcontainers
@Transactional
@SpringBootTest(
    classes = [BombusApplication::class],
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "twilio.auth-token=test-auth-token",
        "twilio.public-base-url=https://example.test",
    ],
)
class UsuarioWhatsappLookupAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: UsuarioWhatsappLookupAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun seedUsuario() {
        jdbcTemplate.update(
            "INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)",
            USUARIO_ID,
            "owner@example.test",
            "hash",
        )
    }

    @Test
    fun `resolves an active link to a mapped customer`() {
        insertWhatsappLink(id = 7, phone = "+5511999999999", displayName = "Ana", active = true)

        val customer = adapter.findActiveByPhone(PhoneNumber("+5511999999999"))

        assertThat(customer).isNotNull
        assertThat(customer!!.whatsappUserId).isEqualTo(7L)
        assertThat(customer.userId).isEqualTo(USUARIO_ID)
        assertThat(customer.displayName).isEqualTo("Ana")
    }

    @Test
    fun `returns null for an inactive link`() {
        insertWhatsappLink(id = 8, phone = "+5511999999999", displayName = "Ana", active = false)

        assertThat(adapter.findActiveByPhone(PhoneNumber("+5511999999999"))).isNull()
    }

    @Test
    fun `returns null when no link exists for the phone`() {
        assertThat(adapter.findActiveByPhone(PhoneNumber("+5511000000000"))).isNull()
    }

    @Test
    fun `matches the phone number exactly, not partially`() {
        insertWhatsappLink(id = 9, phone = "+5511999999999", displayName = null, active = true)

        // one digit short of the stored number must not match
        assertThat(adapter.findActiveByPhone(PhoneNumber("+551199999999"))).isNull()
    }

    private fun insertWhatsappLink(id: Long, phone: String, displayName: String?, active: Boolean) {
        jdbcTemplate.update(
            "INSERT INTO usuario_whatsapp (id, usuario_id, phone_number, display_name, active) VALUES (?, ?, ?, ?, ?)",
            id,
            USUARIO_ID,
            phone,
            displayName,
            active,
        )
    }

    companion object {
        private const val USUARIO_ID = 1L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
    }
}
