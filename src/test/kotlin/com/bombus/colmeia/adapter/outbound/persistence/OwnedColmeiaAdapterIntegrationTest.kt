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
        insertUsuario(OWNER, "o@x.test")
        insertMeliponario(MEL, OWNER)
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
        insertUsuario(OTHER, "p@x.test")
        insertMeliponario(MEL_OTHER, OTHER)

        adapter.insert(2, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)
        adapter.insert(2, 1, MEL_OTHER, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.findByCodeForOwner(OWNER, 2)).hasSize(1)
        assertThat(adapter.findByCodeForOwner(OTHER, 2)).hasSize(1)
        assertThat(adapter.listByOwner(OWNER, emptyList(), 20, 0)).hasSize(1)
        assertThat(adapter.deleteByIdForOwner(OTHER, adapter.findByCodeForOwner(OWNER, 2).first().id)).isFalse()
    }

    @Test
    fun `co-member can find list and delete a hive in a shared yard`() {
        insertUsuario(MEMBER, "m@x.test")
        addMember(MEL, MEMBER)
        val hive = adapter.insert(3, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.findByCodeForOwner(MEMBER, 3)).extracting("id").containsExactly(hive.id)
        assertThat(adapter.listByOwner(MEMBER, emptyList(), 20, 0)).extracting("id").containsExactly(hive.id)
        assertThat(adapter.deleteByIdForOwner(MEMBER, hive.id)).isTrue()
        assertThat(colmeiaCount(hive.id)).isEqualTo(0L)
    }

    @Test
    fun `non-member delete returns false and the row survives`() {
        insertUsuario(OTHER, "p@x.test")
        val hive = adapter.insert(3, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.deleteByIdForOwner(OTHER, hive.id)).isFalse()
        assertThat(colmeiaCount(hive.id)).isEqualTo(1L)
    }

    @Test
    fun `revoked member loses access`() {
        insertUsuario(MEMBER, "m@x.test")
        addMember(MEL, MEMBER)
        val hive = adapter.insert(4, 1, MEL, Instant.parse("2026-01-01T00:00:00Z"), estavelId)
        assertThat(adapter.findByCodeForOwner(MEMBER, 4)).extracting("id").containsExactly(hive.id)

        jdbcTemplate.update(
            "DELETE FROM meliponario_membro WHERE usuario_id = ? AND meliponario_id = ?",
            MEMBER, MEL,
        )

        assertThat(adapter.findByCodeForOwner(MEMBER, 4)).isEmpty()
        assertThat(adapter.listByOwner(MEMBER, emptyList(), 20, 0)).isEmpty()
        assertThat(adapter.findByCodeForOwner(OWNER, 4)).extracting("id").containsExactly(hive.id)
    }

    @Test
    fun `accessible yards are listed ascending by id`() {
        insertUsuario(OTHER, "p@x.test")
        insertUsuario(MEMBER, "m@x.test")
        insertMeliponario(30L, OTHER)
        insertMeliponario(20L, OTHER)
        addMember(30L, MEMBER)
        addMember(20L, MEMBER)

        assertThat(adapter.listMeliponarioIdsByOwner(MEMBER)).containsExactly(20L, 30L)
    }

    @Test
    fun `listMeliponarioIdsByOwner excludes yards the user is not a member of`() {
        insertUsuario(OTHER, "p@x.test")
        insertMeliponario(MEL_OTHER, OTHER)

        assertThat(adapter.listMeliponarioIdsByOwner(OWNER)).containsExactly(MEL)
    }

    @Test
    fun `owner_id without membership grants nothing for list find and delete`() {
        jdbcTemplate.update(
            "INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)",
            MEL_ORPHAN, "orphan", "addr", OWNER,
        )
        val hive = adapter.insert(5, 1, MEL_ORPHAN, Instant.parse("2026-01-01T00:00:00Z"), estavelId)

        assertThat(adapter.listMeliponarioIdsByOwner(OWNER)).containsExactly(MEL)
        assertThat(adapter.findByCodeForOwner(OWNER, 5)).isEmpty()
        assertThat(adapter.listByOwner(OWNER, null, 20, 0)).extracting("id").doesNotContain(hive.id)
        assertThat(adapter.deleteByIdForOwner(OWNER, hive.id)).isFalse()
        assertThat(colmeiaCount(hive.id)).isEqualTo(1L)
    }

    private fun insertUsuario(id: Long, email: String) {
        jdbcTemplate.update(
            "INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)",
            id, email, "h",
        )
    }

    private fun insertMeliponario(id: Long, owner: Long) {
        jdbcTemplate.update(
            "INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)",
            id, "m$id", "addr", owner,
        )
        addMember(id, owner)
    }

    private fun addMember(meliponarioId: Long, usuarioId: Long) {
        jdbcTemplate.update(
            "INSERT INTO meliponario_membro (usuario_id, meliponario_id) VALUES (?, ?)",
            usuarioId, meliponarioId,
        )
    }

    private fun colmeiaCount(id: Long): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM colmeia WHERE id = ?", Long::class.java, id)!!

    companion object {
        private const val OWNER = 1L
        private const val OTHER = 2L
        private const val MEMBER = 3L
        private const val MEL = 10L
        private const val MEL_OTHER = 11L
        private const val MEL_ORPHAN = 12L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
    }
}
