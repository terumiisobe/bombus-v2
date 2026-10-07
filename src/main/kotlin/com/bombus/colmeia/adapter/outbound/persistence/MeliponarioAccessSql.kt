package com.bombus.colmeia.adapter.outbound.persistence

/**
 * The only definition of "usuário :userId may access this meliponário".
 *
 * Access is a row in meliponario_membro. meliponario.owner_id grants nothing.
 * Both fragments bind the named parameter :userId.
 * EXISTS keeps the predicate a filter, so COUNT and GROUP BY never see extra rows.
 */
private fun memberOf(meliponarioIdColumn: String): String = """
    EXISTS (
        SELECT 1 FROM meliponario_membro mm
        WHERE mm.meliponario_id = $meliponarioIdColumn
          AND mm.usuario_id = :userId
    )
"""

/** Requires `colmeia` aliased as `c`. */
internal val ACCESSIBLE_COLMEIA: String = memberOf("c.meliponario_id")

/** Requires `meliponario` aliased as `m`. */
internal val ACCESSIBLE_MELIPONARIO: String = memberOf("m.id")
