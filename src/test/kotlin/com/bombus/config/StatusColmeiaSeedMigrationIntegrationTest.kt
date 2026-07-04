package com.bombus.config

import org.assertj.core.api.Assertions.assertThat
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
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "twilio.auth-token=test-auth-token",
        "twilio.public-base-url=https://example.test",
        "openai.api-key=test-openai-key",
    ],
)
class StatusColmeiaSeedMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `the four canonical statuses are seeded`() {
        val names = jdbcTemplate.queryForList(
            "SELECT name FROM status_colmeia ORDER BY id",
            String::class.java,
        )

        assertThat(names).containsExactly("desenvolvendo", "recuperando", "estavel", "perdida")
    }

    @Test
    fun `inserting a new status without an explicit id does not collide with the seeded ids`() {
        jdbcTemplate.update("INSERT INTO status_colmeia (name) VALUES (?)", "novo")

        val generatedId = jdbcTemplate.queryForObject(
            "SELECT id FROM status_colmeia WHERE name = ?",
            Long::class.java,
            "novo",
        )

        assertThat(generatedId).isGreaterThan(4L)
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
    }
}
