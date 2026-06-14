package com.bombus.config

import com.bombus.colmeia.application.ColmeiaCountProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ColmeiaCountProperties::class)
class ColmeiaConfig
