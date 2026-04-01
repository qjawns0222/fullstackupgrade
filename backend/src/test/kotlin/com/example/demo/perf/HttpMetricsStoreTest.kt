package com.example.demo.perf

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HttpMetricsStoreTest {

    private lateinit var store: HttpMetricsStore

    @BeforeEach
    fun setUp() {
        store = HttpMetricsStore()
    }

    @Test
    fun `record increments summary counters`() {
        store.record("GET /api/resumes", 100L, 200)
        store.record("GET /api/resumes", 200L, 200)
        store.record("GET /api/resumes", 300L, 500)

        val summary = store.getSummary()
        assertEquals(3L, summary.totalRequests)
        assertEquals(1L, summary.totalErrors)
        assertEquals(1, summary.trackedEndpoints)
    }

    @Test
    fun `getStats returns correct percentiles`() {
        // Insert 10 samples with known durations 10, 20, ..., 100 ms
        for (i in 1..10) {
            store.record("POST /api/analysis", (i * 10).toLong(), 200)
        }

        val stats = store.getStats()
        assertEquals(1, stats.size)
        val s = stats.first()
        assertEquals("POST /api/analysis", s.endpoint)
        assertEquals(10L, stats.first().minMs)
        assertEquals(100L, stats.first().maxMs)
        assertEquals(55.0, s.avgMs)          // (10+20+...+100)/10 = 55
        assertTrue(s.p50Ms in 50L..60L)
        assertTrue(s.p95Ms in 90L..100L)
        assertTrue(s.p99Ms in 90L..100L)
    }

    @Test
    fun `error rate calculation is correct`() {
        store.record("DELETE /api/resumes/{id}", 50L, 200)
        store.record("DELETE /api/resumes/{id}", 50L, 500)
        store.record("DELETE /api/resumes/{id}", 50L, 500)

        val stats = store.getStats()
        val s = stats.first()
        assertEquals(2L, s.errorCalls)
        assertEquals(2.0 / 3.0, s.errorRate, 0.001)
    }

    @Test
    fun `multiple endpoints are tracked independently`() {
        store.record("GET /api/resumes", 100L, 200)
        store.record("POST /api/resumes", 200L, 201)
        store.record("GET /api/dashboard", 50L, 200)

        val summary = store.getSummary()
        assertEquals(3L, summary.totalRequests)
        assertEquals(3, summary.trackedEndpoints)
    }

    @Test
    fun `getStatForEndpoint returns null for unknown endpoint`() {
        assertNull(store.getStatForEndpoint("GET /api/nonexistent"))
    }

    @Test
    fun `getStatForEndpoint returns stats for known endpoint`() {
        store.record("GET /api/jobs", 80L, 200)
        val stat = store.getStatForEndpoint("GET /api/jobs")
        assertNotNull(stat)
        assertEquals(1L, stat!!.totalCalls)
        assertEquals(80L, stat.minMs)
    }

    @Test
    fun `empty endpoint stats have zero values`() {
        // Record one sample then force empty window by checking edge
        val stats = store.getStats()
        assertTrue(stats.isEmpty())
        val summary = store.getSummary()
        assertEquals(0L, summary.totalRequests)
    }
}
