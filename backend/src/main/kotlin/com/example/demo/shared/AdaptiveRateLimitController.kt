package com.example.demo.shared

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CircuitBreakerStatus(
    val name: String,
    val state: String,
    val policyName: String,
    val capacityPerMinute: Long,
    val failureRate: Float,
    val numberOfBufferedCalls: Int,
    val numberOfFailedCalls: Int,
    val numberOfSuccessfulCalls: Int,
)

@RestController
@RequestMapping("/api/adaptive-rate-limit")
class AdaptiveRateLimitController(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val adaptiveRateLimitService: AdaptiveRateLimitService,
) {

    @GetMapping("/status")
    fun status(): List<CircuitBreakerStatus> {
        val policySnapshot = adaptiveRateLimitService.snapshot()
        return circuitBreakerRegistry.allCircuitBreakers.map { cb ->
            val metrics = cb.metrics
            val policy = policySnapshot[cb.name] ?: run {
                val defaultPolicy = AdaptiveRateLimitPolicy.Closed
                AdaptiveRateLimitService.PolicySnapshot(
                    cbName = cb.name,
                    policyName = defaultPolicy::class.simpleName ?: "Closed",
                    capacityPerMinute = defaultPolicy.capacity,
                )
            }
            CircuitBreakerStatus(
                name = cb.name,
                state = cb.state.name,
                policyName = policy.policyName,
                capacityPerMinute = policy.capacityPerMinute,
                failureRate = metrics.failureRate,
                numberOfBufferedCalls = metrics.numberOfBufferedCalls,
                numberOfFailedCalls = metrics.numberOfFailedCalls,
                numberOfSuccessfulCalls = metrics.numberOfSuccessfulCalls,
            )
        }
    }
}
