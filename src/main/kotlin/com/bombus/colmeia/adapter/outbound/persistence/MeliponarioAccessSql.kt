package com.bombus.colmeia.adapter.outbound.persistence

private fun memberOf(meliponarioIdColumn: String): String = """
    EXISTS (
        SELECT 1 FROM meliponario_membro mm
        WHERE mm.meliponario_id = $meliponarioIdColumn
          AND mm.usuario_id = :userId
    )
"""

internal val ACCESSIBLE_COLMEIA: String = memberOf("c.meliponario_id")

internal val ACCESSIBLE_MELIPONARIO: String = memberOf("m.id")
