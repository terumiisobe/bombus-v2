package com.bombus.chatbot.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "chatbot.session")
data class ChatSessionProperties(
    val ttl: Duration = Duration.ofMinutes(15),
    val maxContextMessages: Int = 5,
)
