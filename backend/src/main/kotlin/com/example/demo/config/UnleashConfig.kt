package com.example.demo.config

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UnleashConfig {

    @Value("\${unleash.api.url:https://app.unleash-hosted.com/demo/api/}")
    private lateinit var apiUrl: String

    @Value("\${unleash.api.token:default-token}") private lateinit var apiToken: String

    @Bean
    fun unleash(): Unleash {
        val config =
                UnleashConfig.builder()
                        .appName("demo-app")
                        .instanceId("demo-instance")
                        .unleashAPI(apiUrl)
                        .customHttpHeader("Authorization", apiToken)
                        .synchronousFetchOnInitialisation(true)
                        .build()

        return DefaultUnleash(config)
    }
}
