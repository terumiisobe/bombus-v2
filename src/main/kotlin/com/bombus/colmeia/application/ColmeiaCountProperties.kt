package com.bombus.colmeia.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "colmeia")
data class ColmeiaCountProperties(
    val defaultExcludedStatus: String = "perdida",
)
