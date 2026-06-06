package com.bombus.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Deny-by-default web security. The Twilio webhook is the only business endpoint exposed;
 * its real authentication is the X-Twilio-Signature check in the web adapter, so it is
 * permitted at the Spring Security level. springdoc paths are kept open for API docs.
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/v1/whatsapp/webhook").permitAll()
                it.requestMatchers("/v1/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.anyRequest().denyAll()
            }
        return http.build()
    }
}
