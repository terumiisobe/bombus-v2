package com.bombus.colmeia.adapter.outbound.persistence

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
class OwnedColmeiaAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: OwnedColmeiaAdapter

    @Autowired
    private lateinit var statusLookup: StatusColmeiaLookupAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val estavelId: Long get() = statusLookup.findIdByName("estavel")!!
    private val emDesenvolvimentoId: Long get() = statusLookup.findIdByName("em_desenvolvimento")!!

    @BeforeEach
    fun seed() {
        jdbcTemplate.update("INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)", OWNER, "o@x.test", "h")
        jdbcTemplate.update("INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)", MEL, "A", "addr", OWNER)
    }

    @Test
    fun `hard delete frees code for reuse`() {
        val first = adapter.insert(
            code = 1,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            initialStatusId = emDesenvolvimentoId,
        )
        assertThat(adapter.isCodeTaken(MEL, 1)).isTrue()

        assertThat(adapter.deleteByIdForOwner(OWNER, first.id)).isTrue()
        assertThat(adapter.isCodeTaken(MEL, 1)).isFalse()

        val reused = adapter.insert(
            code = 1,
            speciesId = 2,
            meliponarioId = MEL,
            startDate = null,
            initialStatusId = estavelId,
        )
        assertThat(reused.code).isEqualTo(1)
        assertThat(reused.id).isNotEqualTo(first.id)
        assertThat(adapter.listByOwner(OWNER, excludeStatusId = null, limit = 20, offset = 0))
            .extracting("id")
            .containsExactly(reused.id)
    }

    @Test
    fun `ownership isolates find by code and list`() {
        jdbcTemplate.update("INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)", OTHER, "p@x.test", "h")
        jdbcTemplate.update("INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)", MEL_OTHER, "B", "addr", OTHER)

        adapter.insert(2, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)
        adapter.insert(2, 1, MEL_OTHER, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.findByCodeForOwner(OWNER, 2)).hasSize(1)
        assertThat(adapter.findByCodeForOwner(OTHER, 2)).hasSize(1)
        assertThat(adapter.listByOwner(OWNER, null, 20, 0)).hasSize(1)
        assertThat(adapter.deleteByIdForOwner(OTHER, adapter.findByCodeForOwner(OWNER, 2).first().id)).isFalse()
    }

    companion object {
        private const val OWNER = 1L
        private const val OTHER = 2L
        private const val MEL = 10L
        private const val MEL_OTHER = 11L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
    }
}
