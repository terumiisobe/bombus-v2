package com.bombus.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "openai")
data class OpenAiProperties(
    val apiKey: String,
    val model: String = "gpt-4o-mini",
    val baseUrl: String = "https://api.openai.com/v1",
    val timeout: Duration = Duration.ofSeconds(10),
)
