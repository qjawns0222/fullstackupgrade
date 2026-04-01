package com.example.demo.config

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Activates @Observed AOP support so that any bean method annotated with
 * @Observed is automatically wrapped in a Micrometer Observation — enabling
 * latency histograms, span creation, and KeyValue propagation without
 * manual instrumentation at each call site.
 */
@Configuration
class ObservationConfig {

    @Bean
    fun observedAspect(observationRegistry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(observationRegistry)
    }
}
