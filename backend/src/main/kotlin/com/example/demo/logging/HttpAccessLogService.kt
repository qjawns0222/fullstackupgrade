package com.example.demo.logging

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Persists HTTP access log entries to Elasticsearch asynchronously so that
 * the request-response cycle is never blocked by Elasticsearch I/O.
 *
 * The @Async annotation dispatches work to the mailExecutor thread pool which
 * already propagates MDC via its TaskDecorator — ensuring traceId/requestId
 * survive the thread boundary in the async path as well.
 */
@Service
class HttpAccessLogService(
    private val store: HttpAccessLogStore
) {

    private val log = LoggerFactory.getLogger(HttpAccessLogService::class.java)

    @Async("mailExecutor")
    open fun record(
        requestId: String,
        method: String,
        path: String,
        status: Int,
        durationMs: Long,
        clientIp: String,
        userId: String?
    ) {
        try {
            val doc = HttpAccessLogDocument(
                requestId = requestId,
                method = method,
                path = path,
                status = status,
                durationMs = durationMs,
                clientIp = clientIp,
                userId = userId
            )
            store.save(doc)
        } catch (ex: Exception) {
            // Non-fatal — structured logging must never break the main request path
            log.warn("Failed to persist HTTP access log for requestId={}", requestId, ex)
        }
    }

    fun getRecentLogs(page: Int, size: Int): Page<HttpAccessLogDocument> =
        store.findRecent(page, size)

    fun getLogsByStatus(status: Int, page: Int, size: Int): Page<HttpAccessLogDocument> =
        store.findByStatus(status, page, size)

    fun getLogsByUserId(userId: String, page: Int, size: Int): Page<HttpAccessLogDocument> =
        store.findByUserId(userId, page, size)

    fun getLogSummary(): LogSummary = LogSummary(totalRequests = store.count())
}

data class LogSummary(
    val totalRequests: Long
)
