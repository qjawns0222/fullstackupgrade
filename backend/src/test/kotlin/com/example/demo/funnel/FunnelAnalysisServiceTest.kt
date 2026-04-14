package com.example.demo.funnel

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class FunnelAnalysisServiceTest {

    private lateinit var store: FakeUserEventStore
    private lateinit var service: FunnelAnalysisService

    @BeforeEach
    fun setUp() {
        store = FakeUserEventStore()
        service = FunnelAnalysisService(store)
    }

    @Test
    fun `recordEvent saves event via store`() {
        val event = service.recordEvent(
            sessionId = "sess-1",
            userId = "user-1",
            eventType = "RESUME_VIEW",
            resourceId = "resume-42"
        )

        assertEquals("sess-1", event.sessionId)
        assertEquals("RESUME_VIEW", event.eventType)
        assertEquals(1, store.saved.size)
    }

    @Test
    fun `getFunnelStats returns steps with correct conversion rates`() {
        // 10 sessions VIEW, 5 SAVE, 2 DOWNLOAD
        store.sessionCounts["RESUME_VIEW"] = 10L
        store.sessionCounts["RESUME_SAVE"] = 5L
        store.sessionCounts["RESUME_DOWNLOAD"] = 2L

        val stats = service.getFunnelStats(24)

        assertEquals(3, stats.steps.size)
        assertEquals(10L, stats.totalSessions)

        val view = stats.steps[0]
        assertEquals("RESUME_VIEW", view.eventType)
        assertEquals(10L, view.sessionCount)
        assertEquals(100.0, view.conversionRate)

        val save = stats.steps[1]
        assertEquals("RESUME_SAVE", save.eventType)
        assertEquals(5L, save.sessionCount)
        assertEquals(50.0, save.conversionRate)

        val download = stats.steps[2]
        assertEquals("RESUME_DOWNLOAD", download.eventType)
        assertEquals(2L, download.sessionCount)
        assertEquals(20.0, download.conversionRate)
    }

    @Test
    fun `getFunnelStats returns zero conversion when no events`() {
        val stats = service.getFunnelStats(24)

        assertEquals(0L, stats.totalSessions)
        stats.steps.forEach { step ->
            assertEquals(0L, step.sessionCount)
            assertEquals(0.0, step.conversionRate)
        }
    }

    @Test
    fun `getFunnelStats uses periodHours for filtering`() {
        val stats = service.getFunnelStats(48)
        assertEquals(48, stats.periodHours)
    }
}

class FakeUserEventStore : UserEventStore {
    val saved = mutableListOf<UserEvent>()
    val sessionCounts = mutableMapOf<String, Long>()
    private var idSeq = 1L

    override fun save(event: UserEvent) = event.copy(id = idSeq++).also { saved.add(it) }

    override fun countSessionsByEventTypeSince(since: LocalDateTime): Map<String, Long> =
        sessionCounts.toMap()

    override fun countDistinctSessionsByEventTypeSince(eventType: String, since: LocalDateTime): Long =
        sessionCounts[eventType] ?: 0L
}
