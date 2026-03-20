package com.example.demo.query

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory ring-buffer store for recent slow query and N+1 alerts.
 * Listens to Spring application events published by LoggingQueryInspector.
 *
 * Production systems should persist these to Elasticsearch or a metrics store instead.
 */
@Component
class QueryAlertStore {

    private val maxSize = 100

    private val slowQueryAlerts: CopyOnWriteArrayList<SlowQueryEvent> = CopyOnWriteArrayList()
    private val n1Alerts: CopyOnWriteArrayList<N1QueryEvent> = CopyOnWriteArrayList()

    @EventListener
    fun onSlowQuery(event: SlowQueryEvent) {
        if (slowQueryAlerts.size >= maxSize) {
            slowQueryAlerts.removeAt(0)
        }
        slowQueryAlerts.add(event)
    }

    @EventListener
    fun onN1Detected(event: N1QueryEvent) {
        if (n1Alerts.size >= maxSize) {
            n1Alerts.removeAt(0)
        }
        n1Alerts.add(event)
    }

    fun getRecentSlowQueries(): List<SlowQueryEvent> = slowQueryAlerts.toList().takeLast(20)

    fun getRecentN1Alerts(): List<N1QueryEvent> = n1Alerts.toList().takeLast(20)

    fun getTotalSlowQueryCount(): Int = slowQueryAlerts.size

    fun getTotalN1Count(): Int = n1Alerts.size
}
