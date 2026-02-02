package com.example.demo.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ResilienceConfig {

    @Bean
    fun mailServiceCircuitBreaker(): CircuitBreakerConfigCustomizer {
        return CircuitBreakerConfigCustomizer.of("mailService") { builder ->
            builder.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .slidingWindowSize(5)
                    .failureRateThreshold(40.0f) // 2 out of 5 fails -> Open
                    .waitDurationInOpenState(Duration.ofSeconds(20))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
        }
    }
}
