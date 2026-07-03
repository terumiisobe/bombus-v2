package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.ColmeiaVocabularyPort
import com.bombus.colmeia.domain.SpeciesRef
import com.bombus.colmeia.domain.StatusRef
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component

@Component
class ColmeiaVocabularyAdapter(
    private val jdbc: JdbcTemplate,
) : ColmeiaVocabularyPort {

    override fun listSpecies(): List<SpeciesRef> =
        jdbc.query(SPECIES_SQL, SPECIES_MAPPER)

    override fun listStatuses(): List<StatusRef> =
        jdbc.query(STATUS_SQL, STATUS_MAPPER)

    private companion object {
        val SPECIES_SQL = """
            SELECT id, abbreviation, common_name, scientific_name
            FROM especie
            ORDER BY id
        """.trimIndent()

        val STATUS_SQL = """
            SELECT id, name
            FROM status_colmeia
            ORDER BY id
        """.trimIndent()

        val SPECIES_MAPPER = RowMapper { rs, _ ->
            SpeciesRef(
                id = rs.getLong("id"),
                abbreviation = rs.getString("abbreviation"),
                commonName = rs.getString("common_name"),
                scientificName = rs.getString("scientific_name"),
            )
        }

        val STATUS_MAPPER = RowMapper { rs, _ ->
            StatusRef(
                id = rs.getLong("id"),
                name = rs.getString("name"),
            )
        }
    }
}
