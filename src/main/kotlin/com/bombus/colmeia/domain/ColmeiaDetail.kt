package com.bombus.colmeia.domain

/**
 * One hive for detalhar. No internal ids.
 */
data class ColmeiaDetail(
    val code: Int?,
    val speciesCommonName: String,
    val statusName: String?,
    val lastVisit: ColmeiaStatusHistoryEntry?,
)
