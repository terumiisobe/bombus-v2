package com.bombus.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Widens JPA entity and repository scanning to the whole com.bombus tree.
 *
 * The application class lives in com.bombus.config, so Boot's default scanning (its own
 * package and below) would miss the feature packages such as com.bombus.chatbot. This is
 * kept off the application class on purpose so web-slice tests (@WebMvcTest), which exclude
 * arbitrary @Configuration, are not forced to wire JPA.
 */
@Configuration
@EntityScan(basePackages = ["com.bombus"])
@EnableJpaRepositories(basePackages = ["com.bombus"])
class JpaConfig
