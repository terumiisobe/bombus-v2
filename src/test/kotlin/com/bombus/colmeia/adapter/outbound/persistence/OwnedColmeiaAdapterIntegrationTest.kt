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

    private val perdidaId: Long get() = statusLookup.findIdByName("perdida")!!
    private val estavelId: Long get() = statusLookup.findIdByName("estavel")!!

    @BeforeEach
    fun seed() {
        jdbcTemplate.update("INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)", OWNER, "o@x.test", "h")
        jdbcTemplate.update("INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)", MEL, "A", "addr", OWNER)
    }

    @Test
    fun `nextFreeCode skips codes held by non-perdida and reuses after soft-delete status`() {
        val first = adapter.insert(
            code = 1,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            initialStatusId = estavelId,
        )
        assertThat(adapter.nextFreeCode(MEL, perdidaId)).isEqualTo(2)

        adapter.appendStatus(first.id, perdidaId, Instant.parse("2026-02-01T00:00:00Z"))
        assertThat(adapter.isCodeTaken(MEL, 1, perdidaId)).isFalse()
        assertThat(adapter.nextFreeCode(MEL, perdidaId)).isEqualTo(1)

        val reused = adapter.insert(
            code = 1,
            speciesId = 2,
            meliponarioId = MEL,
            startDate = Instant.parse("2026-03-01T00:00:00Z"),
            initialStatusId = estavelId,
        )
        assertThat(reused.code).isEqualTo(1)
        assertThat(reused.id).isNotEqualTo(first.id)

        val listed = adapter.listByOwner(OWNER, excludeStatusId = perdidaId, limit = 20, offset = 0)
        assertThat(listed).extracting("id").containsExactly(reused.id)
    }

    @Test
    fun `ownership isolates find and list`() {
        jdbcTemplate.update("INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)", OTHER, "p@x.test", "h")
        jdbcTemplate.update("INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)", MEL_OTHER, "B", "addr", OTHER)

        val owned = adapter.insert(2, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)
        adapter.insert(2, 1, MEL_OTHER, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.findByIdForOwner(OWNER, owned.id)?.code).isEqualTo(2)
        assertThat(adapter.findByIdForOwner(OTHER, owned.id)).isNull()
        assertThat(adapter.listByOwner(OWNER, null, 20, 0)).hasSize(1)
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
