package com.example.demo.shared

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * CircuitBreaker 상태 변화를 구독해 Bucket4j Rate Limit 정책을 동적으로 조정한다.
 *
 * CB CLOSED  → 정상 정책 (20 req/min)
 * CB HALF_OPEN → 완화 정책 (5 req/min)
 * CB OPEN    → 긴급 제한 정책 (1 req/min)
 */
@Service
class AdaptiveRateLimitService(
    private val proxyManager: ProxyManager<ByteArray>,
) {
    private val log = LoggerFactory.getLogger(AdaptiveRateLimitService::class.java)

    // cbName → 현재 적용 중인 정책
    private val currentPolicies = ConcurrentHashMap<String, AdaptiveRateLimitPolicy>()

    fun registerCircuitBreaker(cb: CircuitBreaker) {
        cb.eventPublisher.onStateTransition { event: CircuitBreakerOnStateTransitionEvent ->
            onStateTransition(cb.name, event)
        }
        log.info("[AdaptiveRateLimit] Registered listener for CircuitBreaker: {}", cb.name)
    }

    private fun onStateTransition(cbName: String, event: CircuitBreakerOnStateTransitionEvent) {
        val transition = event.stateTransition
        val toState = transition.toState  // Kotlin은 getToState() → .toState 자동 변환

        val newPolicy = when (toState) {
            CircuitBreaker.State.OPEN -> AdaptiveRateLimitPolicy.Open
            CircuitBreaker.State.HALF_OPEN -> AdaptiveRateLimitPolicy.HalfOpen
            CircuitBreaker.State.CLOSED -> AdaptiveRateLimitPolicy.Closed
            else -> return // DISABLED, METRICS_ONLY 등은 무시
        }

        val previous = currentPolicies.put(cbName, newPolicy)
        if (previous != newPolicy) {
            log.warn(
                "[AdaptiveRateLimit] CB '{}' → {} : Rate Limit {} req/min (was {} req/min)",
                cbName,
                toState,
                newPolicy.capacity,
                previous?.capacity ?: "default",
            )
        }
    }

    /**
     * 현재 정책으로 Bucket을 반환한다.
     * CB 상태 변화가 없으면 기본 CLOSED 정책이 적용된다.
     */
    fun resolveBucket(cbName: String, bucketKey: String) =
        proxyManager.builder().build(
            bucketKey.toByteArray(Charsets.UTF_8),
            currentPolicyFor(cbName).toBucketConfiguration(),
        )

    fun currentPolicyFor(cbName: String): AdaptiveRateLimitPolicy =
        currentPolicies.getOrDefault(cbName, AdaptiveRateLimitPolicy.Closed)

    /** 현재 모든 CB의 정책 스냅샷 (모니터링 API 용) */
    fun snapshot(): Map<String, PolicySnapshot> =
        currentPolicies.entries.associate { (name, policy) ->
            name to PolicySnapshot(
                cbName = name,
                policyName = policy::class.simpleName ?: "Unknown",
                capacityPerMinute = policy.capacity,
            )
        }

    data class PolicySnapshot(
        val cbName: String,
        val policyName: String,
        val capacityPerMinute: Long,
    )
}
