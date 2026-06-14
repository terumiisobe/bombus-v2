package com.bombus.colmeia.adapter.outbound.persistence

import com.bombus.colmeia.application.port.outbound.StatusColmeiaLookupPort
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class StatusColmeiaLookupAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
) : StatusColmeiaLookupPort {

    override fun findIdByName(name: String): Long? =
        jdbc.queryForList(
            "SELECT id FROM status_colmeia WHERE name = :name",
            MapSqlParameterSource("name", name),
            Long::class.java,
        ).firstOrNull()
}
