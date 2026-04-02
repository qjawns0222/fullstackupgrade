package com.example.demo.controller

import com.example.demo.query.N1QueryEvent
import com.example.demo.query.QueryAlertStore
import com.example.demo.query.QueryMonitorSummary
import com.example.demo.query.SlowQueryEvent
import com.example.demo.query.SlowQueryExplainResult
import com.example.demo.query.SlowQueryExplainRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/query-monitor")
class QueryMonitorController(
    private val queryAlertStore: QueryAlertStore,
    private val explainRepository: SlowQueryExplainRepository
) {

    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<QueryMonitorSummary> {
        return ResponseEntity.ok(
            QueryMonitorSummary(
                totalSlowQueries = queryAlertStore.getTotalSlowQueryCount(),
                totalN1Detections = queryAlertStore.getTotalN1Count(),
                recentSlowQueries = queryAlertStore.getRecentSlowQueries(),
                recentN1Alerts = queryAlertStore.getRecentN1Alerts()
            )
        )
    }

    @GetMapping("/slow-queries")
    fun getSlowQueries(): ResponseEntity<List<SlowQueryEvent>> {
        return ResponseEntity.ok(queryAlertStore.getRecentSlowQueries())
    }

    @GetMapping("/n1-alerts")
    fun getN1Alerts(): ResponseEntity<List<N1QueryEvent>> {
        return ResponseEntity.ok(queryAlertStore.getRecentN1Alerts())
    }

    @GetMapping("/explain-history")
    fun getExplainHistory(): ResponseEntity<List<SlowQueryExplainResult>> {
        val page = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "capturedAt"))
        return ResponseEntity.ok(explainRepository.findAll(page).content)
    }

    @GetMapping("/explain-history/{id}")
    fun getExplainDetail(@PathVariable id: String): ResponseEntity<SlowQueryExplainResult> {
        val result = explainRepository.findById(id)
        return if (result.isPresent) {
            ResponseEntity.ok(result.get())
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
