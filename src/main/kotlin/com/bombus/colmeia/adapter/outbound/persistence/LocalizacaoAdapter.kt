package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.AppendLocalizacao
import com.bombus.colmeia.application.port.outbound.SaveLocalizacaoPort
import com.bombus.colmeia.domain.LocalizacaoReading
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Component
import java.sql.Timestamp

/**
 * JDBC adapter for append-only [localizacao] rows. Membership is enforced on insert via
 * [ACCESSIBLE_COLMEIA] so a caller cannot register GPS on a hive outside their yards.
 */
@Component
class LocalizacaoAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : SaveLocalizacaoPort {

    override fun append(command: AppendLocalizacao): LocalizacaoReading? {
        val keyHolder = GeneratedKeyHolder()
        val updated = jdbc.update(
            APPEND_SQL,
            MapSqlParameterSource()
                .addValue("userId", command.userId)
                .addValue("colmeiaId", command.colmeiaId)
                .addValue("latitude", command.latitude)
                .addValue("longitude", command.longitude)
                .addValue("accuracyMeters", command.accuracyMeters)
                .addValue("at", Timestamp.from(command.recordedAt)),
            keyHolder,
            arrayOf("id"),
        )
        if (updated == 0) return null
        val id = keyHolder.keys?.get("id") as? Number ?: return null
        return LocalizacaoReading(
            id = id.toLong(),
            colmeiaId = command.colmeiaId,
            latitude = command.latitude,
            longitude = command.longitude,
            accuracyMeters = command.accuracyMeters,
            recordedAt = command.recordedAt,
        )
    }

    private companion object {
        val APPEND_SQL = """
            INSERT INTO localizacao (colmeia_id, latitude, longitude, accuracy_meters, recorded_at)
            SELECT :colmeiaId, :latitude, :longitude, :accuracyMeters, :at
            FROM colmeia c
            WHERE c.id = :colmeiaId
              AND $ACCESSIBLE_COLMEIA
        """.trimIndent()
    }
}
