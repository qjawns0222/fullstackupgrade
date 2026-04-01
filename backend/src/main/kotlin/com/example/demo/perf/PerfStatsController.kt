package com.example.demo.perf

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/perf")
class PerfStatsController(
    private val metricsStore: HttpMetricsStore
) {

    /** Returns per-endpoint sliding-window stats, sorted by call volume descending. */
    @GetMapping("/stats")
    fun getStats(
        @RequestParam(defaultValue = "false") errorsOnly: Boolean
    ): ResponseEntity<List<HttpMetricsStore.EndpointStats>> {
        val stats = metricsStore.getStats()
        val filtered = if (errorsOnly) stats.filter { it.errorCalls > 0 } else stats
        return ResponseEntity.ok(filtered)
    }

    /** Aggregate totals across all endpoints. */
    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<HttpMetricsStore.StoreSummary> {
        return ResponseEntity.ok(metricsStore.getSummary())
    }
}
