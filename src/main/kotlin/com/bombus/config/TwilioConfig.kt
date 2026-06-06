package com.bombus.config

import com.twilio.security.RequestValidator
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Composition root for Twilio wiring. The SDK [RequestValidator] is built here from the
 * auth token so adapters depend on it as an injected collaborator.
 */
@Configuration
@EnableConfigurationProperties(TwilioProperties::class)
class TwilioConfig {

    @Bean
    fun twilioRequestValidator(properties: TwilioProperties): RequestValidator =
        RequestValidator(properties.authToken)
}
