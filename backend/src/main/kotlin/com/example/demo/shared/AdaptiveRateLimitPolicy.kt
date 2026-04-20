package com.example.demo.shared

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.BucketConfiguration
import java.time.Duration

/**
 * CircuitBreaker 상태별 Rate Limit 정책.
 * CLOSED: 정상 — 분당 20회
 * HALF_OPEN: 회복 탐색 — 분당 5회 (트래픽 제한)
 * OPEN: 장애 — 분당 1회 (fallback만 허용)
 */
sealed class AdaptiveRateLimitPolicy(
    val capacity: Long,
    val refillTokens: Long,
    val refillPeriod: Duration,
) {
    data object Closed : AdaptiveRateLimitPolicy(20, 20, Duration.ofMinutes(1))
    data object HalfOpen : AdaptiveRateLimitPolicy(5, 5, Duration.ofMinutes(1))
    data object Open : AdaptiveRateLimitPolicy(1, 1, Duration.ofMinutes(1))

    fun toBucketConfiguration(): BucketConfiguration =
        BucketConfiguration.builder()
            .addLimit(
                Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(refillTokens, refillPeriod)
                    .build()
            )
            .build()
}
