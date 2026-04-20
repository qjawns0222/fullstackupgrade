package com.example.demo.shared

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 기동 완료 후 CircuitBreakerRegistry에 등록된
 * 모든 CB에 적응형 Rate Limit 리스너를 붙인다.
 */
@Component
class CircuitBreakerEventListenerConfig(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val adaptiveRateLimitService: AdaptiveRateLimitService,
) {
    private val log = LoggerFactory.getLogger(CircuitBreakerEventListenerConfig::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerListeners() {
        val cbs = circuitBreakerRegistry.allCircuitBreakers
        cbs.forEach { cb ->
            adaptiveRateLimitService.registerCircuitBreaker(cb)
        }
        log.info("[AdaptiveRateLimit] Registered {} CircuitBreaker(s): {}", cbs.size, cbs.map { it.name })
    }
}
