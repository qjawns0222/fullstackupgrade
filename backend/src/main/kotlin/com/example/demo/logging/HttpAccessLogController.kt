package com.example.demo.logging

import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/logs")
class HttpAccessLogController(
    private val httpAccessLogService: HttpAccessLogService
) {

    @GetMapping
    fun getLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: Int?,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<Page<HttpAccessLogDocument>> {
        val result = when {
            status != null -> httpAccessLogService.getLogsByStatus(status, page, size)
            userId != null -> httpAccessLogService.getLogsByUserId(userId, page, size)
            else -> httpAccessLogService.getRecentLogs(page, size)
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<LogSummary> {
        return ResponseEntity.ok(httpAccessLogService.getLogSummary())
    }
}
