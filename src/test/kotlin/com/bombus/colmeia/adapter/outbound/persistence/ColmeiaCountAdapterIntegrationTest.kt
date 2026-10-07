package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.domain.ColmeiaCountFilter
import com.bombus.config.BombusApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
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

// Seeded statuses: 1=desenvolvendo, 2=recuperando, 3=estavel, 4=perdida, 5=desconhecido, 6=vendida.
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
class ColmeiaCountAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: ColmeiaCountAdapter

    @Autowired
    private lateinit var statusLookupAdapter: StatusColmeiaLookupAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val perdidaId: Long get() = statusLookupAdapter.findIdByName("perdida")!!
    private val vendidaId: Long get() = statusLookupAdapter.findIdByName("vendida")!!

    @BeforeEach
    fun seed() {
        insertUsuario(OWNER, "o@x.test")
        insertUsuario(OTHER, "p@x.test")
        insertUsuario(MEMBER, "m@x.test")
        insertMeliponario(MEL_A, OWNER, "A")
        insertMeliponario(MEL_B, OWNER, "B")
        insertMeliponario(MEL_OTHER, OTHER, "C")
        addMember(MEL_A, MEMBER)
        insertMeliponarioWithoutMembership(MEL_ORPHAN, OWNER)

        // c1: species 1, mel A — history perdida then estavel later → current estavel (latest wins).
        insertColmeia(id = 1, speciesId = 1, meliponarioId = MEL_A)
        insertStatus(colmeiaId = 1, statusId = STATUS_PERDIDA, at = "2024-01-01T10:00:00Z")
        insertStatus(colmeiaId = 1, statusId = STATUS_ESTAVEL, at = "2024-01-02T10:00:00Z")

        // c2: species 1, mel A — current perdida.
        insertColmeia(id = 2, speciesId = 1, meliponarioId = MEL_A)
        insertStatus(colmeiaId = 2, statusId = STATUS_PERDIDA, at = "2024-01-01T10:00:00Z")

        // c3: species 1, mel A — no history → "sem status".
        insertColmeia(id = 3, speciesId = 1, meliponarioId = MEL_A)

        // c4: species 2, mel B — current desenvolvendo.
        insertColmeia(id = 4, speciesId = 2, meliponarioId = MEL_B)
        insertStatus(colmeiaId = 4, statusId = STATUS_DESENVOLVENDO, at = "2024-01-01T10:00:00Z")

        // c5: species 2, mel B — current perdida.
        insertColmeia(id = 5, speciesId = 2, meliponarioId = MEL_B)
        insertStatus(colmeiaId = 5, statusId = STATUS_PERDIDA, at = "2024-01-01T10:00:00Z")

        insertColmeia(id = 6, speciesId = 1, meliponarioId = MEL_OTHER)
        insertStatus(colmeiaId = 6, statusId = STATUS_ESTAVEL, at = "2024-01-01T10:00:00Z")

        // c7: species 2, mel B — current vendida (also default-excluded).
        insertColmeia(id = 7, speciesId = 2, meliponarioId = MEL_B)
        insertStatus(colmeiaId = 7, statusId = STATUS_VENDIDA, at = "2024-01-01T10:00:00Z")

        insertColmeia(id = 8, speciesId = 1, meliponarioId = MEL_ORPHAN)
        insertStatus(colmeiaId = 8, statusId = STATUS_ESTAVEL, at = "2024-01-01T10:00:00Z")
    }

    @Test
    fun `co-member who is not owner counts that yard's hives`() {
        val count = adapter.countByOwner(
            MEMBER,
            ColmeiaCountFilter(excludeStatusIds = setOf(perdidaId, vendidaId)),
        )

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `owner_id without membership grants nothing`() {
        val count = adapter.countByOwner(
            OWNER,
            ColmeiaCountFilter(excludeStatusIds = setOf(perdidaId, vendidaId)),
        )

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `plain count excludes perdida and vendida but keeps sem status, summing across meliponarios`() {
        // Living: c1 (estavel), c3 (sem status), c4 (desenvolvendo) = 3. c2/c5 perdida, c7 vendida excluded.
        val count = adapter.countByOwner(
            OWNER,
            ColmeiaCountFilter(excludeStatusIds = setOf(perdidaId, vendidaId)),
        )

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `species-only count excludes perdida and vendida and keeps sem status of that species`() {
        // Species 1 living: c1 (estavel), c3 (sem status); c2 (perdida) excluded = 2.
        val count = adapter.countByOwner(
            OWNER,
            ColmeiaCountFilter(speciesId = 1, excludeStatusIds = setOf(perdidaId, vendidaId)),
        )

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `explicit perdida status is returnable when the exclusion is not applied`() {
        // c2 and c5 are perdida = 2.
        val count = adapter.countByOwner(OWNER, ColmeiaCountFilter(includeStatusId = perdidaId))

        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `explicit vendida status is returnable when the exclusion is not applied`() {
        val count = adapter.countByOwner(OWNER, ColmeiaCountFilter(includeStatusId = vendidaId))

        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `explicit status returns exactly that status using the latest history row`() {
        // c1 ended estavel (latest beats its earlier perdida row) = 1.
        val count = adapter.countByOwner(OWNER, ColmeiaCountFilter(includeStatusId = STATUS_ESTAVEL))

        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `an unknown status id yields zero`() {
        val count = adapter.countByOwner(OWNER, ColmeiaCountFilter(includeStatusId = 9999))

        assertThat(count).isZero()
    }

    @Test
    fun `per-species breakdown counts every species without excluding perdida or vendida`() {
        // species 1: c1, c2, c3 = 3; species 2: c4, c5, c7 = 3. c6 belongs to OTHER and is excluded.
        val perSpecies = adapter.breakdownBySpecies(OWNER, ColmeiaCountFilter())

        assertThat(perSpecies).extracting("speciesId", "count")
            .containsExactly(
                tuple(1L, 3L),
                tuple(2L, 3L),
            )
    }

    @Test
    fun `per-status breakdown includes perdida vendida and a sem-status (null) group`() {
        // desenvolvendo: c4 = 1; estavel: c1 = 1; perdida: c2, c5 = 2; vendida: c7 = 1; sem status: c3 = 1.
        val perStatus = adapter.breakdownByStatus(OWNER, ColmeiaCountFilter())

        assertThat(perStatus).extracting("statusId", "count")
            .containsExactlyInAnyOrder(
                tuple(STATUS_DESENVOLVENDO, 1L),
                tuple(STATUS_ESTAVEL, 1L),
                tuple(STATUS_PERDIDA, 2L),
                tuple(STATUS_VENDIDA, 1L),
                tuple(null, 1L),
            )
        assertThat(perStatus.sumOf { it.count }).isEqualTo(6)
    }

    private fun insertUsuario(id: Long, email: String) {
        jdbcTemplate.update(
            "INSERT INTO usuario (id, email, password_hash) VALUES (?, ?, ?)",
            id, email, "h",
        )
    }

    private fun insertMeliponario(id: Long, owner: Long, name: String) {
        jdbcTemplate.update(
            "INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)",
            id, name, "addr", owner,
        )
        addMember(id, owner)
    }

    private fun insertMeliponarioWithoutMembership(id: Long, ownerId: Long) {
        jdbcTemplate.update(
            "INSERT INTO meliponario (id, name, address, owner_id) VALUES (?, ?, ?, ?)",
            id, "orphan", "addr", ownerId,
        )
    }

    private fun addMember(meliponarioId: Long, usuarioId: Long) {
        jdbcTemplate.update(
            "INSERT INTO meliponario_membro (usuario_id, meliponario_id) VALUES (?, ?)",
            usuarioId, meliponarioId,
        )
    }

    private fun insertColmeia(id: Long, speciesId: Long, meliponarioId: Long) {
        jdbcTemplate.update(
            "INSERT INTO colmeia (id, code, species_id, meliponario_id) VALUES (?, ?, ?, ?)",
            id, id.toInt(), speciesId, meliponarioId,
        )
    }

    private fun insertStatus(colmeiaId: Long, statusId: Long, at: String) {
        jdbcTemplate.update(
            "INSERT INTO colmeia_status_historico (colmeia_id, status_id, recorded_at) VALUES (?, ?, CAST(? AS timestamptz))",
            colmeiaId, statusId, at,
        )
    }

    companion object {
        private const val OWNER = 1L
        private const val OTHER = 2L
        private const val MEMBER = 3L
        private const val MEL_A = 10L
        private const val MEL_B = 11L
        private const val MEL_OTHER = 12L
        private const val MEL_ORPHAN = 13L

        private const val STATUS_DESENVOLVENDO = 1L
        private const val STATUS_ESTAVEL = 3L
        private const val STATUS_PERDIDA = 4L
        private const val STATUS_VENDIDA = 6L

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
    }
}
