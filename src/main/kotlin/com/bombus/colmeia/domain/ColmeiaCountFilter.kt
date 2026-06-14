package com.bombus.colmeia.domain

data class ColmeiaCountFilter(
    val speciesId: Long? = null,
    val includeStatusId: Long? = null,
    val excludeStatusId: Long? = null,
) {
    init {
        require(includeStatusId == null || excludeStatusId == null) {
            "includeStatusId and excludeStatusId are mutually exclusive"
        }
    }
}
