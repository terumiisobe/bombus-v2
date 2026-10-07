package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.AppendColmeiaStatus
import com.bombus.colmeia.application.port.outbound.OwnedColmeiaPort
import com.bombus.colmeia.domain.ColmeiaSummary
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Instant

@Component
class OwnedColmeiaAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : OwnedColmeiaPort {

    override fun listMeliponarioIdsByOwner(userId: Long): List<Long> =
        jdbc.queryForList(
            LIST_ACCESSIBLE_MELIPONARIO_IDS_SQL,
            MapSqlParameterSource("userId", userId),
            Long::class.java,
        )

    override fun listByOwner(
        userId: Long,
        excludeStatusIds: Collection<Long>,
        limit: Int,
        offset: Int,
    ): List<ColmeiaSummary> =
        jdbc.query(
            LIST_SQL,
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("hasExclude", excludeStatusIds.isNotEmpty())
                .addValue("excludeStatusIds", excludeStatusIds.ifEmpty { listOf(-1L) })
                .addValue("limit", limit)
                .addValue("offset", offset),
            SUMMARY_MAPPER,
        )

    override fun findByCodeForOwner(
        userId: Long,
        code: Int,
    ): List<ColmeiaSummary> =
        jdbc.query(
            FIND_BY_CODE_SQL,
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("code", code),
            SUMMARY_MAPPER,
        )

    override fun isCodeTaken(
        meliponarioId: Long,
        code: Int,
        exceptColmeiaId: Long?,
        ignoreStatusIds: Collection<Long>,
    ): Boolean {
        val count = jdbc.queryForObject(
            CODE_TAKEN_SQL,
            MapSqlParameterSource()
                .addValue("meliponarioId", meliponarioId)
                .addValue("code", code)
                .addValue("exceptColmeiaId", exceptColmeiaId)
                .addValue("hasIgnore", ignoreStatusIds.isNotEmpty())
                .addValue("ignoreStatusIds", ignoreStatusIds.ifEmpty { listOf(-1L) }),
            Long::class.java,
        ) ?: 0L
        return count > 0
    }

    override fun insert(
        code: Int?,
        speciesId: Long,
        meliponarioId: Long,
        startDate: Instant?,
        initialStatusId: Long,
    ): ColmeiaSummary {
        val keyHolder = GeneratedKeyHolder()
        jdbc.jdbcTemplate.update({ connection ->
            val ps = connection.prepareStatement(
                "INSERT INTO colmeia (code, species_id, meliponario_id, start_date) VALUES (?, ?, ?, ?)",
                arrayOf("id"),
            )
            if (code == null) ps.setObject(1, null)
            else ps.setInt(1, code)
            ps.setLong(2, speciesId)
            ps.setLong(3, meliponarioId)
            if (startDate == null) ps.setTimestamp(4, null)
            else ps.setTimestamp(4, Timestamp.from(startDate))
            ps
        }, keyHolder)
        val id = keyHolder.key!!.toLong()
        jdbc.update(
            "INSERT INTO colmeia_status_historico (colmeia_id, status_id, recorded_at) VALUES (:id, :statusId, NOW())",
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("statusId", initialStatusId),
        )
        return findByIdUnchecked(id)!!
    }

    override fun appendStatus(append: AppendColmeiaStatus): ColmeiaSummary? {
        val updated = jdbc.update(
            """
            INSERT INTO colmeia_status_historico (
                colmeia_id, status_id, recorded_at, recorded_by_user_id, note, source
            )
            SELECT :id, :statusId, :at, :recordedByUserId, :note, :source
            WHERE EXISTS (SELECT 1 FROM colmeia WHERE id = :id)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", append.colmeiaId)
                .addValue("statusId", append.statusId)
                .addValue("at", Timestamp.from(append.recordedAt))
                .addValue("recordedByUserId", append.recordedByUserId)
                .addValue("note", append.note)
                .addValue("source", append.source),
        )
        if (updated == 0) return null
        return findByIdUnchecked(append.colmeiaId)
    }

    override fun deleteByIdForOwner(userId: Long, colmeiaId: Long): Boolean {
        val updated = jdbc.update(
            DELETE_ACCESSIBLE_SQL,
            MapSqlParameterSource()
                .addValue("colmeiaId", colmeiaId)
                .addValue("userId", userId),
        )
        return updated > 0
    }

    private fun findByIdUnchecked(colmeiaId: Long): ColmeiaSummary? =
        jdbc.query(
            FIND_BY_ID_UNSCOPED_SQL,
            MapSqlParameterSource("colmeiaId", colmeiaId),
            SUMMARY_MAPPER,
        ).firstOrNull()

    private companion object {
        const val LATEST_STATUS = """
            LEFT JOIN LATERAL (
                SELECT h.status_id
                FROM colmeia_status_historico h
                WHERE h.colmeia_id = c.id
                ORDER BY h.recorded_at DESC, h.id DESC
                LIMIT 1
            ) cur ON true
        """

        const val SUMMARY_SELECT = """
            SELECT c.id AS id,
                   c.code AS code,
                   c.species_id AS species_id,
                   e.abbreviation AS species_abbreviation,
                   e.common_name AS species_common_name,
                   c.meliponario_id AS meliponario_id,
                   c.start_date AS start_date,
                   cur.status_id AS status_id,
                   (SELECT s.name FROM status_colmeia s WHERE s.id = cur.status_id) AS status_name
            FROM colmeia c
            JOIN especie e ON e.id = c.species_id
            $LATEST_STATUS
        """

        val LIST_ACCESSIBLE_MELIPONARIO_IDS_SQL = """
            SELECT m.id
            FROM meliponario m
            WHERE $ACCESSIBLE_MELIPONARIO
            ORDER BY m.id
        """.trimIndent()

        // Keep "sem status" (NULL) when excluding; NOT IN alone would drop NULL rows.
        val LIST_SQL = """
            $SUMMARY_SELECT
            WHERE $ACCESSIBLE_COLMEIA
              AND (
                CAST(:hasExclude AS BOOLEAN) = FALSE
                OR cur.status_id IS NULL
                OR cur.status_id NOT IN (:excludeStatusIds)
              )
            ORDER BY c.code NULLS LAST, c.id
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        val FIND_BY_ID_UNSCOPED_SQL = """
            $SUMMARY_SELECT
            WHERE c.id = :colmeiaId
        """.trimIndent()

        val FIND_BY_CODE_SQL = """
            $SUMMARY_SELECT
            WHERE $ACCESSIBLE_COLMEIA
              AND c.code = :code
            ORDER BY c.id
        """.trimIndent()

        val DELETE_ACCESSIBLE_SQL = """
            DELETE FROM colmeia c
            WHERE c.id = :colmeiaId
              AND $ACCESSIBLE_COLMEIA
        """.trimIndent()

        val CODE_TAKEN_SQL = """
            SELECT COUNT(*)
            FROM colmeia c
            $LATEST_STATUS
            WHERE c.meliponario_id = :meliponarioId
              AND c.code = :code
              AND (CAST(:exceptColmeiaId AS BIGINT) IS NULL OR c.id IS DISTINCT FROM CAST(:exceptColmeiaId AS BIGINT))
              AND (
                CAST(:hasIgnore AS BOOLEAN) = FALSE
                OR cur.status_id IS NULL
                OR cur.status_id NOT IN (:ignoreStatusIds)
              )
        """.trimIndent()

        val SUMMARY_MAPPER = RowMapper { rs, _ ->
            ColmeiaSummary(
                id = rs.getLong("id"),
                code = rs.getInt("code").takeUnless { rs.wasNull() },
                speciesId = rs.getLong("species_id"),
                speciesAbbreviation = rs.getString("species_abbreviation"),
                speciesCommonName = rs.getString("species_common_name"),
                meliponarioId = rs.getLong("meliponario_id"),
                startDate = rs.getTimestamp("start_date")?.toInstant(),
                statusId = rs.getLong("status_id").takeUnless { rs.wasNull() },
                statusName = rs.getString("status_name"),
            )
        }
    }
}
