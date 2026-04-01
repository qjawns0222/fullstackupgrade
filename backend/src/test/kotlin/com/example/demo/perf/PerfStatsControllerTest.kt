package com.example.demo.perf

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

class PerfStatsControllerTest {

    private val metricsStore = mock(HttpMetricsStore::class.java)
    private val controller = PerfStatsController(metricsStore)

    @Test
    fun `getStats returns all stats when errorsOnly is false`() {
        val stats = listOf(
            HttpMetricsStore.EndpointStats(
                endpoint = "GET /api/resumes",
                totalCalls = 10, errorCalls = 0, errorRate = 0.0,
                avgMs = 50.0, p50Ms = 50, p95Ms = 80, p99Ms = 95,
                minMs = 10, maxMs = 120
            )
        )
        `when`(metricsStore.getStats()).thenReturn(stats)

        val response = controller.getStats(errorsOnly = false)
        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.size)
    }

    @Test
    fun `getStats filters errors only when errorsOnly is true`() {
        val stats = listOf(
            HttpMetricsStore.EndpointStats(
                endpoint = "GET /api/resumes",
                totalCalls = 10, errorCalls = 0, errorRate = 0.0,
                avgMs = 50.0, p50Ms = 50, p95Ms = 80, p99Ms = 95,
                minMs = 10, maxMs = 120
            ),
            HttpMetricsStore.EndpointStats(
                endpoint = "POST /api/upload",
                totalCalls = 5, errorCalls = 3, errorRate = 0.6,
                avgMs = 200.0, p50Ms = 180, p95Ms = 400, p99Ms = 450,
                minMs = 100, maxMs = 500
            )
        )
        `when`(metricsStore.getStats()).thenReturn(stats)

        val response = controller.getStats(errorsOnly = true)
        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.size)
        assertEquals("POST /api/upload", response.body?.first()?.endpoint)
    }

    @Test
    fun `getSummary returns store summary`() {
        val summary = HttpMetricsStore.StoreSummary(
            totalRequests = 100L,
            totalErrors = 5L,
            trackedEndpoints = 10,
            windowSeconds = 300L
        )
        `when`(metricsStore.getSummary()).thenReturn(summary)

        val response = controller.getSummary()
        assertEquals(200, response.statusCode.value())
        assertEquals(100L, response.body?.totalRequests)
        assertEquals(5L, response.body?.totalErrors)
    }
}
