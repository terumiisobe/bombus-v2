package com.bombus.adapter.inbound.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Lightweight liveness endpoint for Render (and similar hosts). Kept free of DB/AI
 * work so a health probe never depends on outbound dependencies.
 */
@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "UP"))
}
