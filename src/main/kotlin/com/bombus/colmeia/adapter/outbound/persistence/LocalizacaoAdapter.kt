package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
import com.bombus.colmeia.application.port.outbound.AppendLocalizacaoClear
import com.bombus.colmeia.application.port.outbound.LoadLocalizacaoPort
import com.bombus.colmeia.application.port.outbound.SaveLocalizacaoPort
import com.bombus.colmeia.domain.LocalizacaoReading
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.Timestamp

/**
 * JDBC adapter for append-only [localizacao] rows (live pins and clear events).
 * Membership is enforced on insert/select via [ACCESSIBLE_COLMEIA].
 */
@Component
class LocalizacaoAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : SaveLocalizacaoPort, LoadLocalizacaoPort {

    override fun append(command: AppendLocalizacao): LocalizacaoReading? =
        insertRow(
            userId = command.userId,
            colmeiaId = command.colmeiaId,
            latitude = command.latitude,
            longitude = command.longitude,
            recordedAt = command.recordedAt,
        )

    override fun appendClear(command: AppendLocalizacaoClear): LocalizacaoReading? =
        insertRow(
            userId = command.userId,
            colmeiaId = command.colmeiaId,
            latitude = null,
            longitude = null,
            recordedAt = command.recordedAt,
        )

    override fun findCurrent(userId: Long, colmeiaId: Long): LocalizacaoReading? {
        val rows = jdbc.query(
            FIND_CURRENT_SQL,
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("colmeiaId", colmeiaId),
        ) { rs, _ ->
            LocalizacaoReading(
                id = rs.getLong("id"),
                colmeiaId = colmeiaId,
                latitude = rs.getBigDecimal("latitude"),
                longitude = rs.getBigDecimal("longitude"),
                recordedAt = rs.getTimestamp("recorded_at").toInstant(),
            )
        }
        return rows.firstOrNull()
    }

    private fun insertRow(
        userId: Long,
        colmeiaId: Long,
        latitude: BigDecimal?,
        longitude: BigDecimal?,
        recordedAt: java.time.Instant,
    ): LocalizacaoReading? {
        val keyHolder = GeneratedKeyHolder()
        val updated = jdbc.update(
            APPEND_SQL,
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("colmeiaId", colmeiaId)
                .addValue("latitude", latitude)
                .addValue("longitude", longitude)
                .addValue("at", Timestamp.from(recordedAt)),
            keyHolder,
            arrayOf("id"),
        )
        if (updated == 0) return null
        val id = keyHolder.keys?.get("id") as? Number ?: return null
        return LocalizacaoReading(
            id = id.toLong(),
            colmeiaId = colmeiaId,
            latitude = latitude,
            longitude = longitude,
            recordedAt = recordedAt,
        )
    }

    private companion object {
        val APPEND_SQL = """
            INSERT INTO localizacao (colmeia_id, latitude, longitude, recorded_at)
            SELECT :colmeiaId, :latitude, :longitude, :at
            FROM colmeia c
            WHERE c.id = :colmeiaId
              AND $ACCESSIBLE_COLMEIA
        """.trimIndent()

        val FIND_CURRENT_SQL = """
            SELECT l.id, l.latitude, l.longitude, l.recorded_at
            FROM localizacao l
            JOIN colmeia c ON c.id = l.colmeia_id
            WHERE l.colmeia_id = :colmeiaId
              AND $ACCESSIBLE_COLMEIA
            ORDER BY l.recorded_at DESC, l.id DESC
            LIMIT 1
        """.trimIndent()
    }
}
