package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
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
import java.math.BigDecimal
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
class MeliponarioMapAdapterIntegrationTest {

    @Autowired
    private lateinit var mapAdapter: MeliponarioMapAdapter

    @Autowired
    private lateinit var owned: OwnedColmeiaAdapter

    @Autowired
    private lateinit var localizacao: LocalizacaoAdapter

    @Autowired
    private lateinit var statusLookup: StatusColmeiaLookupAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val desenvolvendoId: Long get() = statusLookup.findIdByName("desenvolvendo")!!
    private val estavelId: Long get() = statusLookup.findIdByName("estavel")!!

    @BeforeEach
    fun seed() {
        insertUsuario(OWNER, "o@x.test")
        insertUsuario(OTHER, "p@x.test")
        insertMeliponario(MEL, OWNER, "Meliponário Isobe")
        insertMeliponario(MEL_OTHER, OTHER, "Outro")
    }

    @Test
    fun `loads farm hives with latest status location and note in one membership scoped read`() {
        val pinned = owned.insert(
            code = 12,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = Instant.parse("2025-06-30T00:00:00Z"),
            initialStatusId = desenvolvendoId,
            recordedByUserId = OWNER,
        )
        owned.appendStatus(
            AppendColmeiaStatus(
                colmeiaId = pinned.id,
                statusId = estavelId,
                recordedAt = Instant.parse("2026-10-09T15:00:00Z"),
                recordedByUserId = OWNER,
                note = "Trocar o fundo.",
            ),
        )
        localizacao.append(
            AppendLocalizacao(
                userId = OWNER,
                colmeiaId = pinned.id,
                latitude = BigDecimal("-23.947800"),
                longitude = BigDecimal("-46.986600"),
                recordedAt = Instant.parse("2026-10-01T12:00:00Z"),
            ),
        )
        owned.insert(
            code = 13,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = null,
            initialStatusId = desenvolvendoId,
            recordedByUserId = OWNER,
        )

        val view = mapAdapter.loadForMember(OWNER, MEL)!!

        assertThat(view.farmName).isEqualTo("Meliponário Isobe")
        assertThat(view.hives).hasSize(2)
        val first = view.hives.first { it.code == 12 }
        assertThat(first.statusName).isEqualTo("estavel")
        assertThat(first.notes).isEqualTo("Trocar o fundo.")
        assertThat(first.latitude).isEqualByComparingTo(BigDecimal("-23.947800"))
        assertThat(first.longitude).isEqualByComparingTo(BigDecimal("-46.986600"))
        assertThat(first.lastInspectionAt).isEqualTo(Instant.parse("2026-10-09T15:00:00Z"))
        val unpinned = view.hives.first { it.code == 13 }
        assertThat(unpinned.latitude).isNull()
        assertThat(unpinned.longitude).isNull()
        assertThat(unpinned.statusName).isEqualTo("desenvolvendo")
    }

    @Test
    fun `outsider gets null even when yard exists`() {
        assertThat(mapAdapter.loadForMember(OTHER, MEL)).isNull()
    }

    @Test
    fun `empty accessible yard returns farm with no hives`() {
        val view = mapAdapter.loadForMember(OWNER, MEL)!!
        assertThat(view.farmName).isEqualTo("Meliponário Isobe")
        assertThat(view.hives).isEmpty()
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
        jdbcTemplate.update(
            "INSERT INTO meliponario_membro (usuario_id, meliponario_id) VALUES (?, ?)",
            owner, id,
        )
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
