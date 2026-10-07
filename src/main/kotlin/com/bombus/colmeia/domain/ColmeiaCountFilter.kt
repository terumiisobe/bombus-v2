package com.bombus.colmeia.domain

data class ColmeiaCountFilter(
    val speciesId: Long? = null,
    val includeStatusId: Long? = null,
    val excludeStatusIds: Set<Long> = emptySet(),
) {
    init {
        require(includeStatusId == null || excludeStatusIds.isEmpty()) {
            "includeStatusId and excludeStatusIds are mutually exclusive"
        }
    }
}
