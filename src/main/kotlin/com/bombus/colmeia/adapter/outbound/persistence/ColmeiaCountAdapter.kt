package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.ColmeiaCountPort
import com.bombus.colmeia.domain.ColmeiaCountFilter
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class ColmeiaCountAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : ColmeiaCountPort {

    override fun countByOwner(userId: Long, filter: ColmeiaCountFilter): Long {
        val params = MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("speciesId", filter.speciesId)
            .addValue("includeStatusId", filter.includeStatusId)
            .addValue("excludeStatusId", filter.excludeStatusId)
        return jdbc.queryForObject(COUNT_SQL, params, Long::class.java) ?: 0L
    }

    private companion object {
        // IS DISTINCT FROM (not <>) is required so excludeStatusId drops only that status
        // while keeping "sem status" (NULL current status) colmeias.
        val COUNT_SQL = """
            SELECT COUNT(*)
            FROM colmeia c
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
        """.trimIndent()
    }
}
