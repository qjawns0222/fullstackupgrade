package com.example.demo.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Populates MDC with per-request correlation fields so every log line emitted
 * during request processing carries: requestId, userId, httpMethod, httpPath,
 * httpStatus, durationMs, clientIp.
 *
 * Because logstash-logback-encoder serialises the MDC map as top-level JSON
 * fields, these become first-class fields in Elasticsearch — queryable without
 * any Logstash pipeline parsing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)   // just after RateLimitFilter
class RequestCorrelationFilter(
    private val httpAccessLogService: HttpAccessLogService
) : OncePerRequestFilter() {

    private val log: Logger = LoggerFactory.getLogger(RequestCorrelationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val requestId = request.getHeader("X-Request-Id") ?: UUID.randomUUID().toString()
        val startMs = System.currentTimeMillis()

        // Populate MDC before any downstream processing
        MDC.put(MdcKeys.REQUEST_ID, requestId)
        MDC.put(MdcKeys.HTTP_METHOD, request.method)
        MDC.put(MdcKeys.HTTP_PATH, request.requestURI)
        MDC.put(MdcKeys.CLIENT_IP, resolveClientIp(request))

        // Propagate X-Request-Id so clients can correlate responses
        response.setHeader("X-Request-Id", requestId)

        try {
            chain.doFilter(request, response)
        } finally {
            val durationMs = System.currentTimeMillis() - startMs
            val status = response.status

            MDC.put(MdcKeys.HTTP_STATUS, status.toString())
            MDC.put(MdcKeys.DURATION_MS, durationMs.toString())

            log.info("HTTP {} {} {} {}ms", request.method, request.requestURI, status, durationMs)

            // Persist access log entry asynchronously to Elasticsearch
            httpAccessLogService.record(
                requestId = requestId,
                method = request.method,
                path = request.requestURI,
                status = status,
                durationMs = durationMs,
                clientIp = resolveClientIp(request),
                userId = MDC.get(MdcKeys.USER_ID)
            )

            MDC.clear()
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (forwarded.isNullOrBlank()) request.remoteAddr
        else forwarded.split(",").first().trim()
    }
}
