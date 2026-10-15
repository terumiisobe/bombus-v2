package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.LoadMeliponarioMapPort
import com.bombus.colmeia.domain.MeliponarioMapHive
import com.bombus.colmeia.domain.MeliponarioMapView
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.ZoneOffset

/**
 * Single membership-gated read for the map UI.
 *
 * Strategy: one SQL round-trip with dual `LEFT JOIN LATERAL` subqueries —
 * latest status historico (status + note + recorded_at) and latest localizacao per hive —
 * so there is no N+1 for inspection or pin. Empty yards still return via `LEFT JOIN colmeia`.
 */
@Component
class MeliponarioMapAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : LoadMeliponarioMapPort {

    override fun loadForMember(userId: Long, meliponarioId: Long): MeliponarioMapView? {
        val rows = jdbc.query(
            LOAD_SQL,
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("meliponarioId", meliponarioId),
        ) { rs, _ ->
            Row(
                meliponarioId = rs.getLong("meliponario_id"),
                farmName = rs.getString("farm_name"),
                colmeiaId = rs.getLong("colmeia_id").takeUnless { rs.wasNull() },
                code = rs.getInt("code").takeUnless { rs.wasNull() },
                latitude = rs.getBigDecimal("latitude"),
                longitude = rs.getBigDecimal("longitude"),
                statusName = rs.getString("status_name"),
                startDate = rs.getTimestamp("start_date")?.toInstant(),
                lastInspectionAt = rs.getTimestamp("last_inspection_at")?.toInstant(),
                notes = rs.getString("notes"),
                locationRecordedAt = rs.getTimestamp("location_recorded_at")?.toInstant(),
            )
        }
        if (rows.isEmpty()) return null

        val first = rows.first()
        val hives = rows.mapNotNull { row ->
            if (row.colmeiaId == null) return@mapNotNull null
            MeliponarioMapHive(
                code = row.code,
                latitude = row.latitude,
                longitude = row.longitude,
                statusName = row.statusName,
                addedAt = row.startDate?.atZone(ZoneOffset.UTC)?.toLocalDate(),
                addedAtInstant = row.startDate,
                lastInspectionAt = row.lastInspectionAt,
                notes = row.notes,
                locationRecordedAt = row.locationRecordedAt,
            )
        }
        return MeliponarioMapView(
            meliponarioId = first.meliponarioId,
            farmName = first.farmName,
            hives = hives,
        )
    }

    private data class Row(
        val meliponarioId: Long,
        val farmName: String,
        val colmeiaId: Long?,
        val code: Int?,
        val latitude: BigDecimal?,
        val longitude: BigDecimal?,
        val statusName: String?,
        val startDate: java.time.Instant?,
        val lastInspectionAt: java.time.Instant?,
        val notes: String?,
        val locationRecordedAt: java.time.Instant?,
    )

    private companion object {
        // Indexes: idx_colmeia_status_hist_atual, idx_localizacao_atual
        val LOAD_SQL = """
            SELECT
                m.id AS meliponario_id,
                m.name AS farm_name,
                c.id AS colmeia_id,
                c.code AS code,
                c.start_date AS start_date,
                cur.status_name AS status_name,
                cur.recorded_at AS last_inspection_at,
                cur.note AS notes,
                loc.latitude AS latitude,
                loc.longitude AS longitude,
                loc.recorded_at AS location_recorded_at
            FROM meliponario m
            LEFT JOIN colmeia c ON c.meliponario_id = m.id
            LEFT JOIN LATERAL (
                SELECT h.recorded_at, h.note, s.name AS status_name
                FROM colmeia_status_historico h
                JOIN status_colmeia s ON s.id = h.status_id
                WHERE h.colmeia_id = c.id
                ORDER BY h.recorded_at DESC, h.id DESC
                LIMIT 1
            ) cur ON true
            LEFT JOIN LATERAL (
                SELECT l.latitude, l.longitude, l.recorded_at
                FROM localizacao l
                WHERE l.colmeia_id = c.id
                ORDER BY l.recorded_at DESC, l.id DESC
                LIMIT 1
            ) loc ON true
            WHERE m.id = :meliponarioId
              AND $ACCESSIBLE_MELIPONARIO
            ORDER BY c.code NULLS LAST, c.id
        """.trimIndent()
    }
}
