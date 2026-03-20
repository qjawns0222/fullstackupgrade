package com.example.demo.query

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Default QueryInspector that logs slow queries and N+1 patterns,
 * then publishes Spring application events for further processing (e.g. metrics, alerting).
 */
@Component
class LoggingQueryInspector(
    private val eventPublisher: ApplicationEventPublisher
) : QueryInspector {

    private val log = LoggerFactory.getLogger(LoggingQueryInspector::class.java)

    override fun onSlowQuery(event: SlowQueryEvent) {
        log.warn(
            "[SLOW-QUERY] {}ms (threshold: {}ms) | {}.{} | SQL: {}",
            event.elapsedTimeMs,
            event.thresholdMs,
            event.callerClass,
            event.callerMethod,
            event.sql.take(500)
        )
        eventPublisher.publishEvent(event)
    }

    override fun onN1Detected(event: N1QueryEvent) {
        log.warn(
            "[N+1-DETECTED] SQL executed {} times (threshold: {}) | {}.{} | SQL: {}",
            event.executionCount,
            event.thresholdCount,
            event.callerClass,
            event.callerMethod,
            event.normalizedSql.take(300)
        )
        eventPublisher.publishEvent(event)
    }
}
