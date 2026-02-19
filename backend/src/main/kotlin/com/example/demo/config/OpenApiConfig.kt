package com.example.demo.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
                .info(
                        Info().title("AI Blog API")
                                .description("API documentation for AI Blog service")
                                .version("v1.0")
                )
    }
}
