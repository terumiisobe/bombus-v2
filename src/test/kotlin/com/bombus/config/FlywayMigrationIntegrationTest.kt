package com.bombus.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "twilio.auth-token=test-auth-token",
        "twilio.public-base-url=https://example.test",
        "openai.api-key=test-openai-key",
    ],
)
class FlywayMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `flyway applies the baseline and reference data migrations`() {
        val applied = jdbcTemplate.queryForList(
            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
            String::class.java,
        )

        assertThat(applied).contains("1", "2")
    }

    @Test
    fun `core tables exist in the migrated schema`() {
        val tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String::class.java,
        )

        assertThat(tables).contains(
            "especie",
            "usuario",
            "meliponario",
            "status_colmeia",
            "colmeia",
            "colmeia_status_historico",
            "localizacao",
            "usuario_whatsapp",
            "sessao_chat",
        )
    }

    @Test
    fun `species reference data is seeded`() {
        val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM especie", Long::class.java)

        assertThat(count).isEqualTo(10L)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
    }
}
