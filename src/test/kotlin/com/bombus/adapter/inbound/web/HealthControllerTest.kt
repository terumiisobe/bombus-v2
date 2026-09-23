package com.bombus.adapter.inbound.web

import com.bombus.config.BombusApplication
import com.bombus.config.SecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(HealthController::class)
@ContextConfiguration(classes = [BombusApplication::class])
@Import(SecurityConfig::class)
class HealthControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `health is publicly reachable`() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }
}
