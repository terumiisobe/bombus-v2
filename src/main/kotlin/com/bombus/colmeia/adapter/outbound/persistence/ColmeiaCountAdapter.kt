package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.ColmeiaCountPort
import com.bombus.colmeia.domain.ColmeiaCountFilter
import com.bombus.colmeia.domain.SpeciesCount
import com.bombus.colmeia.domain.StatusCount
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class ColmeiaCountAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : ColmeiaCountPort {

    override fun countByOwner(userId: Long, filter: ColmeiaCountFilter): Long =
        jdbc.queryForObject(COUNT_SQL, params(userId, filter), Long::class.java) ?: 0L

    override fun breakdownBySpecies(userId: Long, filter: ColmeiaCountFilter): List<SpeciesCount> =
        jdbc.query(BREAKDOWN_BY_SPECIES_SQL, params(userId, filter), SPECIES_MAPPER)

    override fun breakdownByStatus(userId: Long, filter: ColmeiaCountFilter): List<StatusCount> =
        jdbc.query(BREAKDOWN_BY_STATUS_SQL, params(userId, filter), STATUS_MAPPER)

    private fun params(userId: Long, filter: ColmeiaCountFilter) =
        MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("speciesId", filter.speciesId)
            .addValue("includeStatusId", filter.includeStatusId)
            .addValue("excludeStatusId", filter.excludeStatusId)

    private companion object {
        // IS DISTINCT FROM (not <>) is required so excludeStatusId drops only that status
        // while keeping "sem status" (NULL current status) colmeias.
        private const val LATEST_STATUS_AND_FILTERS = """
            JOIN meliponario m ON m.id = c.meliponario_id
            LEFT JOIN LATERAL (
                SELECT h.status_id
                FROM colmeia_status_historico h
                WHERE h.colmeia_id = c.id
                ORDER BY h.recorded_at DESC, h.id DESC
                LIMIT 1
            ) cur ON true
            WHERE m.owner_id = :userId
              AND (CAST(:speciesId AS BIGINT) IS NULL OR c.species_id = CAST(:speciesId AS BIGINT))
              AND (CAST(:includeStatusId AS BIGINT) IS NULL OR cur.status_id = CAST(:includeStatusId AS BIGINT))
              AND (CAST(:excludeStatusId AS BIGINT) IS NULL OR cur.status_id IS DISTINCT FROM CAST(:excludeStatusId AS BIGINT))
        """

        val COUNT_SQL = """
            SELECT COUNT(*)
            FROM colmeia c
            $LATEST_STATUS_AND_FILTERS
        """.trimIndent()

        val BREAKDOWN_BY_SPECIES_SQL = """
            SELECT c.species_id AS species_id,
                   e.abbreviation AS abbreviation,
                   e.common_name AS common_name,
                   COUNT(*) AS cnt
            FROM colmeia c
            JOIN especie e ON e.id = c.species_id
            $LATEST_STATUS_AND_FILTERS
            GROUP BY c.species_id, e.abbreviation, e.common_name
            ORDER BY c.species_id
        """.trimIndent()

        // The NULL cur.status_id group is the "sem status" subgroup; its name resolves to NULL.
        val BREAKDOWN_BY_STATUS_SQL = """
            SELECT cur.status_id AS status_id,
                   (SELECT s.name FROM status_colmeia s WHERE s.id = cur.status_id) AS status_name,
                   COUNT(*) AS cnt
            FROM colmeia c
            $LATEST_STATUS_AND_FILTERS
            GROUP BY cur.status_id
            ORDER BY cur.status_id NULLS LAST
        """.trimIndent()

        val SPECIES_MAPPER = RowMapper { rs, _ ->
            SpeciesCount(
                speciesId = rs.getLong("species_id"),
                abbreviation = rs.getString("abbreviation"),
                commonName = rs.getString("common_name"),
                count = rs.getLong("cnt"),
            )
        }

        val STATUS_MAPPER = RowMapper { rs, _ ->
            val statusId = rs.getLong("status_id").takeUnless { rs.wasNull() }
            StatusCount(
                statusId = statusId,
                statusName = rs.getString("status_name"),
                count = rs.getLong("cnt"),
            )
        }
    }
}
