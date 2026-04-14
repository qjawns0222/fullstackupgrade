package com.example.demo.funnel

import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class FunnelStep(
    val eventType: String,
    val sessionCount: Long,
    val conversionRate: Double
)

data class FunnelStats(
    val steps: List<FunnelStep>,
    val totalSessions: Long,
    val periodHours: Int
)

@Service
class FunnelAnalysisService(private val store: UserEventStore) {

    companion object {
        val FUNNEL_STEPS = listOf(
            "RESUME_VIEW",
            "RESUME_SAVE",
            "RESUME_DOWNLOAD"
        )
    }

    fun recordEvent(
        sessionId: String,
        userId: String?,
        eventType: String,
        resourceId: String? = null,
        metadata: String? = null
    ): UserEvent = store.save(
        UserEvent(
            sessionId = sessionId,
            userId = userId,
            eventType = eventType,
            resourceId = resourceId,
            metadata = metadata
        )
    )

    fun getFunnelStats(periodHours: Int = 24): FunnelStats {
        val since = LocalDateTime.now().minusHours(periodHours.toLong())
        val countsByType = store.countSessionsByEventTypeSince(since)

        val topCount = FUNNEL_STEPS.firstOrNull()
            ?.let { countsByType[it] ?: 0L }
            ?: 0L

        val steps = FUNNEL_STEPS.map { eventType ->
            val count = countsByType[eventType] ?: 0L
            FunnelStep(
                eventType = eventType,
                sessionCount = count,
                conversionRate = if (topCount == 0L) 0.0
                    else (count.toDouble() / topCount * 100).let {
                        Math.round(it * 10) / 10.0
                    }
            )
        }

        return FunnelStats(
            steps = steps,
            totalSessions = topCount,
            periodHours = periodHours
        )
    }
}
