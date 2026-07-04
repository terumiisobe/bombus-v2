package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.config.BombusApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

// Vocabulary is seeded by migrations: 10 especie rows (V2) and 4 status_colmeia rows.
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
class ColmeiaVocabularyAdapterIntegrationTest {

    @Autowired
    private lateinit var adapter: ColmeiaVocabularyAdapter

    @Test
    fun `lists the seeded species ordered by id`() {
        val species = adapter.listSpecies()

        assertThat(species).hasSize(10)
        assertThat(species.map { it.id }).isSorted
        assertThat(species.first().abbreviation).isEqualTo("JT")
        assertThat(species.first().commonName).isEqualTo("Jataí")
        assertThat(species.first().scientificName).isEqualTo("Tetragosnisca angustula")
    }

    @Test
    fun `lists the seeded statuses ordered by id`() {
        val statuses = adapter.listStatuses()

        assertThat(statuses.map { it.name })
            .containsExactly("desenvolvendo", "recuperando", "estavel", "perdida")
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:15")
    }
}
