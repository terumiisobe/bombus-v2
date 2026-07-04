package com.bombus.config

import com.bombus.chatbot.application.ChatSessionProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(ChatSessionProperties::class)
class ChatbotConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
