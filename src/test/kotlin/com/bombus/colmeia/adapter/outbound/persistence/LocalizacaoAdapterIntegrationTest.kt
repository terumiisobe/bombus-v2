package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
import com.bombus.colmeia.application.port.outbound.AppendLocalizacaoClear
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
class LocalizacaoAdapterIntegrationTest {

    @Autowired
    private lateinit var owned: OwnedColmeiaAdapter

    @Autowired
    private lateinit var localizacao: LocalizacaoAdapter

    @Autowired
    private lateinit var statusLookup: StatusColmeiaLookupAdapter

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val desenvolvendoId: Long get() = statusLookup.findIdByName("desenvolvendo")!!

    @BeforeEach
    fun seed() {
        insertUsuario(OWNER, "o@x.test")
        insertUsuario(OTHER, "p@x.test")
        insertMeliponario(MEL, OWNER)
        insertMeliponario(MEL_OTHER, OTHER)
    }

    @Test
    fun `append stores row for member and rejects outsider`() {
        val hive = owned.insert(
            code = 42,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = Instant.parse("2026-01-01T00:00:00Z"),
            initialStatusId = desenvolvendoId,
            recordedByUserId = OWNER,
        )
        val at = Instant.parse("2026-10-15T15:00:00Z")

        val reading = localizacao.append(
            AppendLocalizacao(
                userId = OWNER,
                colmeiaId = hive.id,
                latitude = BigDecimal("-23.550520"),
                longitude = BigDecimal("-46.633308"),
                recordedAt = at,
            ),
        )

        assertThat(reading).isNotNull
        assertThat(reading!!.colmeiaId).isEqualTo(hive.id)
        assertThat(reading.latitude).isEqualByComparingTo(BigDecimal("-23.550520"))
        assertThat(reading.longitude).isEqualByComparingTo(BigDecimal("-46.633308"))
        assertThat(reading.recordedAt).isEqualTo(at)

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM localizacao WHERE colmeia_id = ?",
            Long::class.java,
            hive.id,
        )
        assertThat(count).isEqualTo(1L)

        val denied = localizacao.append(
            AppendLocalizacao(
                userId = OTHER,
                colmeiaId = hive.id,
                latitude = BigDecimal("1"),
                longitude = BigDecimal("1"),
                recordedAt = at.plusSeconds(60),
            ),
        )
        assertThat(denied).isNull()
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM localizacao WHERE colmeia_id = ?",
                Long::class.java,
                hive.id,
            ),
        ).isEqualTo(1L)
    }

    @Test
    fun `append keeps history so latest row is current`() {
        val hive = owned.insert(
            code = 7,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = null,
            initialStatusId = desenvolvendoId,
            recordedByUserId = OWNER,
        )
        val t1 = Instant.parse("2026-10-15T10:00:00Z")
        val t2 = Instant.parse("2026-10-15T11:00:00Z")

        localizacao.append(
            AppendLocalizacao(OWNER, hive.id, BigDecimal("10.000000"), BigDecimal("20.000000"), t1),
        )
        localizacao.append(
            AppendLocalizacao(OWNER, hive.id, BigDecimal("11.000000"), BigDecimal("21.000000"), t2),
        )

        val latestLat = jdbcTemplate.queryForObject(
            """
            SELECT latitude FROM localizacao
            WHERE colmeia_id = ?
            ORDER BY recorded_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            BigDecimal::class.java,
            hive.id,
        )
        assertThat(latestLat).isEqualByComparingTo(BigDecimal("11.000000"))
        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM localizacao WHERE colmeia_id = ?",
                Long::class.java,
                hive.id,
            ),
        ).isEqualTo(2L)
    }

    @Test
    fun `appendClear keeps prior pin history and makes current pin none`() {
        val hive = owned.insert(
            code = 8,
            speciesId = 1,
            meliponarioId = MEL,
            startDate = null,
            initialStatusId = desenvolvendoId,
            recordedByUserId = OWNER,
        )
        val pinAt = Instant.parse("2026-10-15T10:00:00Z")
        val clearAt = Instant.parse("2026-10-15T12:00:00Z")

        localizacao.append(
            AppendLocalizacao(OWNER, hive.id, BigDecimal("-23.5"), BigDecimal("-46.6"), pinAt),
        )
        val cleared = localizacao.appendClear(
            AppendLocalizacaoClear(OWNER, hive.id, clearAt),
        )

        assertThat(cleared).isNotNull
        assertThat(cleared!!.isClearEvent).isTrue()
        assertThat(cleared.latitude).isNull()
        assertThat(cleared.longitude).isNull()
        assertThat(cleared.recordedAt).isEqualTo(clearAt)

        assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM localizacao WHERE colmeia_id = ?",
                Long::class.java,
                hive.id,
            ),
        ).isEqualTo(2L)

        val latest = jdbcTemplate.queryForMap(
            """
            SELECT latitude, longitude FROM localizacao
            WHERE colmeia_id = ?
            ORDER BY recorded_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            hive.id,
        )
        assertThat(latest["latitude"]).isNull()
        assertThat(latest["longitude"]).isNull()
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
