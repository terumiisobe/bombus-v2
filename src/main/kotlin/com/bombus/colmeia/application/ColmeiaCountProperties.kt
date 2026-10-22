package com.bombus.colmeia.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "colmeia")
data class ColmeiaCountProperties(
    /**
     * Status names excluded from plain/species counts and from default list views
     * **at mark** (when current status becomes one of these).
     *
     * Code soft-release is **not** driven by this list alone: a disposing hive still
     * holds its code while it has a live pin; release when never-pinned at mark or
     * after a clear event (see [OwnedColmeiaPort.isCodeTaken]).
     */
    val defaultExcludedStatuses: List<String> = listOf("perdida", "vendida"),
) {
    fun excludedStatusLabel(): String = defaultExcludedStatuses.joinToString(", ")

    /**
     * True when [statusName] is a disposition status (`perdida` / `vendida`).
     * Used for count/list exclusion and "inactive" resolve paths — **not** a complete
     * answer for whether the code is free (presence / live pin also matters).
     */
    fun releasesCode(statusName: String?): Boolean =
        statusName != null && statusName in defaultExcludedStatuses

    /** Alias for disposition check; prefer this name when presence gates code release. */
    fun isDispositionStatus(statusName: String?): Boolean = releasesCode(statusName)
}
