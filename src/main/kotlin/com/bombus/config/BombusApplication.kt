package com.bombus.config

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["com.bombus"],
    exclude = [UserDetailsServiceAutoConfiguration::class],
)
class BombusApplication

fun main(args: Array<String>) {
    runApplication<BombusApplication>(*args)
}
