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
    private val desenvolvendoId: Long get() = statusLookup.findIdByName("desenvolvendo")!!
    private val perdidaId: Long get() = statusLookup.findIdByName("perdida")!!
    private val vendidaId: Long get() = statusLookup.findIdByName("vendida")!!

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
            initialStatusId = desenvolvendoId,
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
        assertThat(adapter.listByOwner(OWNER, excludeStatusIds = emptyList(), limit = 20, offset = 0))
            .extracting("id")
            .containsExactly(reused.id)
    }

    @Test
    fun `perdida keeps code but soft-uniqueness frees it for reuse`() {
        val first = adapter.insert(
            code = 9,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            initialStatusId = desenvolvendoId,
        )
        assertThat(adapter.isCodeTaken(MEL, 9)).isTrue()

        val lost = adapter.appendStatus(first.id, perdidaId, Instant.parse("2026-02-01T00:00:00Z"))!!
        assertThat(lost.code).isEqualTo(9)
        assertThat(adapter.isCodeTaken(MEL, 9, ignoreStatusIds = listOf(perdidaId, vendidaId))).isFalse()

        val reused = adapter.insert(9, 2, MEL, null, estavelId)
        assertThat(reused.code).isEqualTo(9)
        assertThat(reused.id).isNotEqualTo(first.id)
        assertThat(adapter.findByCodeForOwner(OWNER, 9)).hasSize(2)
    }

    @Test
    fun `vendida keeps code but soft-uniqueness frees it for reuse`() {
        val first = adapter.insert(
            code = 8,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            initialStatusId = desenvolvendoId,
        )
        val sold = adapter.appendStatus(first.id, vendidaId, Instant.parse("2026-02-01T00:00:00Z"))!!
        assertThat(sold.code).isEqualTo(8)
        assertThat(adapter.isCodeTaken(MEL, 8, ignoreStatusIds = listOf(perdidaId, vendidaId))).isFalse()

        val reused = adapter.insert(8, 2, MEL, null, estavelId)
        assertThat(reused.code).isEqualTo(8)
        assertThat(adapter.findByCodeForOwner(OWNER, 8)).extracting("id")
            .containsExactlyInAnyOrder(first.id, reused.id)
    }

    @Test
    fun `list excludes multiple statuses while keeping sem status`() {
        val living = adapter.insert(1, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), desenvolvendoId)
        val lost = adapter.insert(2, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), perdidaId)
        val sold = adapter.insert(3, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), vendidaId)
        jdbcTemplate.update(
            "INSERT INTO colmeia (id, code, species_id, meliponario_id) VALUES (?, ?, ?, ?)",
            99L, 4, 1L, MEL,
        )

        val listed = adapter.listByOwner(
            OWNER,
            excludeStatusIds = listOf(perdidaId, vendidaId),
            limit = 20,
            offset = 0,
        )

        assertThat(listed).extracting("id").containsExactly(living.id, 99L)
        assertThat(listed).extracting("id").doesNotContain(lost.id, sold.id)
    }

    @Test
    fun `ownership isolates find by code and list`() {
        jdbcTemplate.update("INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)", OTHER, "p@x.test", "h")
        jdbcTemplate.update("INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)", MEL_OTHER, "B", "addr", OTHER)

        adapter.insert(2, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)
        adapter.insert(2, 1, MEL_OTHER, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.findByCodeForOwner(OWNER, 2)).hasSize(1)
        assertThat(adapter.findByCodeForOwner(OTHER, 2)).hasSize(1)
        assertThat(adapter.listByOwner(OWNER, emptyList(), 20, 0)).hasSize(1)
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
