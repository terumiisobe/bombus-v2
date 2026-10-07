package com.bombus.config

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

/**
 * Runs outside Spring so Flyway does not migrate ahead of the seed.
 * The normal suite migrates an empty database, so the backfill selects zero rows there.
 */
@Testcontainers
class MeliponarioMembroBackfillMigrationTest {

    @Test
    fun `backfill grants each existing owner membership`() {
        fun flyway(target: String?): Flyway {
            val config = Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            if (target != null) {
                config.target(MigrationVersion.fromVersion(target))
            }
            return config.load()
        }

        flyway(target = "4").migrate()

        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("INSERT INTO usuario (id, email, password_hash) VALUES (1, 'u1@x.test', 'h')")
                statement.execute("INSERT INTO usuario (id, email, password_hash) VALUES (2, 'u2@x.test', 'h')")
                statement.execute(
                    "INSERT INTO meliponario (id, name, address, owner_id) VALUES (1, 'Yard 1', 'addr', 2)",
                )
            }
        }

        flyway(target = null).migrate()

        val memberships = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT usuario_id, meliponario_id FROM meliponario_membro ORDER BY 1, 2",
                ).use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.getLong(1) to rs.getLong(2))
                        }
                    }
                }
            }
        }

        assertThat(memberships).containsExactly(2L to 1L)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
    }
}
