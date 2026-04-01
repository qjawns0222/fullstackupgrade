package com.example.demo.perf

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * In-memory sliding-window store for HTTP request metrics.
 *
 * Keeps a bounded deque of raw RequestSample entries per normalized path
 * (method + path template). The window is 5 minutes by default.
 * Samples older than the window are purged lazily on each read.
 *
 * This intentionally avoids Redis or Elasticsearch for the hot path —
 * the goal is sub-millisecond recording overhead per request.
 */
@Component
class HttpMetricsStore {

    companion object {
        private const val WINDOW_SECONDS = 300L      // 5-minute sliding window
        private const val MAX_SAMPLES_PER_KEY = 2000 // cap memory per endpoint
    }

    data class RequestSample(
        val durationMs: Long,
        val status: Int,
        val recordedAt: Instant = Instant.now()
    )

    data class EndpointStats(
        val endpoint: String,
        val totalCalls: Long,
        val errorCalls: Long,
        val errorRate: Double,       // 0.0 – 1.0
        val avgMs: Double,
        val p50Ms: Long,
        val p95Ms: Long,
        val p99Ms: Long,
        val minMs: Long,
        val maxMs: Long,
        val windowSeconds: Long = WINDOW_SECONDS
    )

    private val samples = ConcurrentHashMap<String, ConcurrentLinkedDeque<RequestSample>>()
    // All-time counters per endpoint (never expire)
    private val totalCounter = ConcurrentHashMap<String, Long>()
    private val errorCounter = ConcurrentHashMap<String, Long>()

    fun record(endpoint: String, durationMs: Long, status: Int) {
        val deque = samples.computeIfAbsent(endpoint) { ConcurrentLinkedDeque() }
        deque.addLast(RequestSample(durationMs, status))

        // Bound memory: evict oldest if over cap
        while (deque.size > MAX_SAMPLES_PER_KEY) {
            deque.pollFirst()
        }

        totalCounter.merge(endpoint, 1L, Long::plus)
        if (status >= 500) {
            errorCounter.merge(endpoint, 1L, Long::plus)
        }
    }

    fun getStats(): List<EndpointStats> {
        val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
        return samples.entries.map { (endpoint, deque) ->
            val recent = deque.filter { it.recordedAt.isAfter(cutoff) }
            buildStats(endpoint, recent)
        }.sortedByDescending { it.totalCalls }
    }

    fun getStatForEndpoint(endpoint: String): EndpointStats? {
        val deque = samples[endpoint] ?: return null
        val cutoff = Instant.now().minusSeconds(WINDOW_SECONDS)
        val recent = deque.filter { it.recordedAt.isAfter(cutoff) }
        return buildStats(endpoint, recent)
    }

    fun getSummary(): StoreSummary {
        val total = totalCounter.values.sum()
        val errors = errorCounter.values.sum()
        return StoreSummary(
            totalRequests = total,
            totalErrors = errors,
            trackedEndpoints = samples.size,
            windowSeconds = WINDOW_SECONDS
        )
    }

    private fun buildStats(endpoint: String, recent: List<RequestSample>): EndpointStats {
        if (recent.isEmpty()) {
            return EndpointStats(
                endpoint = endpoint,
                totalCalls = totalCounter[endpoint] ?: 0,
                errorCalls = errorCounter[endpoint] ?: 0,
                errorRate = 0.0,
                avgMs = 0.0,
                p50Ms = 0, p95Ms = 0, p99Ms = 0,
                minMs = 0, maxMs = 0
            )
        }
        val sorted = recent.map { it.durationMs }.sorted()
        val errors = recent.count { it.status >= 500 }.toLong()
        return EndpointStats(
            endpoint = endpoint,
            totalCalls = recent.size.toLong(),
            errorCalls = errors,
            errorRate = if (recent.isEmpty()) 0.0 else errors.toDouble() / recent.size,
            avgMs = sorted.average(),
            p50Ms = percentile(sorted, 50),
            p95Ms = percentile(sorted, 95),
            p99Ms = percentile(sorted, 99),
            minMs = sorted.first(),
            maxMs = sorted.last()
        )
    }

    private fun percentile(sorted: List<Long>, p: Int): Long {
        if (sorted.isEmpty()) return 0
        val index = (sorted.size * p / 100).coerceAtMost(sorted.size - 1)
        return sorted[index]
    }

    data class StoreSummary(
        val totalRequests: Long,
        val totalErrors: Long,
        val trackedEndpoints: Int,
        val windowSeconds: Long
    )
}
