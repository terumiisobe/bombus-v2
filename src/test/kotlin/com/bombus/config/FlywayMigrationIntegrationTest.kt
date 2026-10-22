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

        assertThat(applied).contains("1", "2", "3", "4", "5", "6", "7", "8", "9")
    }

    @Test
    fun `localizacao has no accuracy_meters after V8`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'localizacao'
            """.trimIndent(),
            String::class.java,
        )

        assertThat(columns).contains("latitude", "longitude", "recorded_at", "colmeia_id")
        assertThat(columns).doesNotContain("accuracy_meters")
    }

    @Test
    fun `localizacao coords are nullable after V9 for clear events`() {
        val nullable = jdbcTemplate.queryForList(
            """
            SELECT column_name, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'localizacao'
              AND column_name IN ('latitude', 'longitude')
            """.trimIndent(),
        )
        assertThat(nullable).hasSize(2)
        assertThat(nullable).allMatch { it["is_nullable"] == "YES" }

        val check = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.check_constraints
            WHERE constraint_name = 'localizacao_coords_both_or_neither'
            """.trimIndent(),
            Long::class.java,
        )
        assertThat(check).isEqualTo(1L)
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
            "meliponario_membro",
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
        val postgres = PostgreSQLContainer("postgres:17")
    }
}
