package com.bombus.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Deny-by-default web security. The Twilio webhook is the only business endpoint exposed;
 * its real authentication is the X-Twilio-Signature check in the web adapter, so it is
 * permitted at the Spring Security level. springdoc paths are kept open for API docs.
 *
 * Map-UI CORS is scoped to the meliponarios API paths (local Vite + hosted frontend) —
 * the webhook path is intentionally not CORS-enabled (Twilio does not browser-preflight).
 */
@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/health").permitAll()
                it.requestMatchers("/v1/whatsapp/webhook").permitAll()
                // Interim: identity via X-User-Id; membership enforced in JDBC (meliponario_membro).
                it.requestMatchers("/v1/meliponarios/**").permitAll()
                it.requestMatchers("/v1/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.anyRequest().denyAll()
            }
        return http.build()
    }

    /**
     * Allows the map frontend (local Vite and hosted Render app) to call the map snapshot
     * API with the interim `X-User-Id` header. Methods are GET + OPTIONS only. Credentials
     * are off — auth is the custom header, not cookies.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val mapUi = CorsConfiguration().apply {
            allowedOrigins = MAP_UI_ORIGINS
            allowedMethods = listOf("GET", "OPTIONS")
            allowedHeaders = listOf("X-User-Id", "Accept", "Content-Type")
            allowCredentials = false
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/v1/meliponarios/**", mapUi)
        }
    }

    companion object {
        val MAP_UI_ORIGINS = listOf(
            "http://localhost:5173",
            "http://localhost:3000",
            "https://bombus-frontend.onrender.com",
        )
    }
}
