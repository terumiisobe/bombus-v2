package com.bombus.colmeia.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "colmeia")
data class ColmeiaCountProperties(
    /**
     * Status names excluded from plain/species counts and from default list views.
     * The same set releases a hive's `code` for reuse via soft uniqueness (the code
     * stays on the row; [isCodeTaken] ignores these statuses).
     */
    val defaultExcludedStatuses: List<String> = listOf("perdida", "vendida"),
) {
    fun excludedStatusLabel(): String = defaultExcludedStatuses.joinToString(", ")

    fun releasesCode(statusName: String?): Boolean =
        statusName != null && statusName in defaultExcludedStatuses
}
