package com.bombus.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
@EnableConfigurationProperties(OpenAiProperties::class)
class OpenAiConfig {

    @Bean
    fun openAiRestClient(properties: OpenAiProperties): RestClient {
        val settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(properties.timeout)
            .withReadTimeout(properties.timeout)
        val requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings)
        return RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeaders { it.setBearerAuth(properties.apiKey) }
            .build()
    }
}
