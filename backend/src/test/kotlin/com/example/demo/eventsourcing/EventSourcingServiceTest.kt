package com.example.demo.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventSourcingServiceTest {

    private lateinit var store: FakeDomainEventStore
    private lateinit var service: EventSourcingService

    @BeforeEach
    fun setUp() {
        store = FakeDomainEventStore()
        service = EventSourcingService(store, ObjectMapper())
    }

    @Test
    fun `record stores event and returns it`() {
        val event = service.record(
            aggregateType = "Resume",
            aggregateId = "42",
            eventType = "RESUME_CREATED",
            payload = mapOf("title" to "Backend Developer"),
            actor = "user1"
        )

        assertEquals("Resume", event.aggregateType)
        assertEquals("42", event.aggregateId)
        assertEquals("RESUME_CREATED", event.eventType)
        assertEquals("user1", event.actor)
        assertTrue(event.eventPayload.contains("Backend Developer"))
    }

    @Test
    fun `replayAggregate returns events in order`() {
        service.record("Resume", "1", "CREATED", mapOf("v" to 1))
        service.record("Resume", "1", "UPDATED", mapOf("v" to 2))
        service.record("Resume", "2", "CREATED", mapOf("v" to 1))

        val events = service.replayAggregate("Resume", "1")
        assertEquals(2, events.size)
        assertEquals("CREATED", events[0].eventType)
        assertEquals("UPDATED", events[1].eventType)
    }

    @Test
    fun `recentEvents returns limited list`() {
        repeat(10) { i -> service.record("Job", "$i", "APPLIED", mapOf("i" to i)) }

        val recent = service.recentEvents(5)
        assertEquals(5, recent.size)
    }

    @Test
    fun `stats returns total count and aggregate breakdown`() {
        service.record("Resume", "1", "CREATED", emptyMap<String, Any>())
        service.record("Resume", "2", "CREATED", emptyMap<String, Any>())
        service.record("Job", "1", "APPLIED", emptyMap<String, Any>())

        val stats = service.stats()
        assertEquals(3L, stats.totalEvents)
        assertTrue(stats.aggregateTypes.any { it.aggregateType == "Resume" && it.count == 2L })
        assertTrue(stats.aggregateTypes.any { it.aggregateType == "Job" && it.count == 1L })
    }

    @Test
    fun `periodEvents filters by time range`() {
        val now = LocalDateTime.now()
        store.appendWithTime(DomainEvent(aggregateType = "Resume", aggregateId = "1", eventType = "OLD", eventPayload = "{}", actor = null, occurredAt = now.minusDays(2)))
        store.appendWithTime(DomainEvent(aggregateType = "Resume", aggregateId = "1", eventType = "NEW", eventPayload = "{}", actor = null, occurredAt = now))

        val events = service.periodEvents("Resume", now.minusHours(1), now.plusHours(1))
        assertEquals(1, events.size)
        assertEquals("NEW", events[0].eventType)
    }
}

class FakeDomainEventStore : DomainEventStore {
    private val events = mutableListOf<DomainEvent>()
    private var seq = 1L

    override fun append(event: DomainEvent): DomainEvent {
        val saved = DomainEvent(
            id = seq++,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            eventPayload = event.eventPayload,
            actor = event.actor,
            occurredAt = event.occurredAt
        )
        events.add(saved)
        return saved
    }

    fun appendWithTime(event: DomainEvent): DomainEvent {
        val saved = DomainEvent(
            id = seq++,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            eventPayload = event.eventPayload,
            actor = event.actor,
            occurredAt = event.occurredAt
        )
        events.add(saved)
        return saved
    }

    override fun findByAggregate(aggregateType: String, aggregateId: String) =
        events.filter { it.aggregateType == aggregateType && it.aggregateId == aggregateId }
            .sortedBy { it.occurredAt }

    override fun findRecent(limit: Int) =
        events.sortedByDescending { it.occurredAt }.take(limit)

    override fun findByAggregateTypeAndPeriod(aggregateType: String, from: LocalDateTime, to: LocalDateTime) =
        events.filter { it.aggregateType == aggregateType && it.occurredAt in from..to }
            .sortedBy { it.occurredAt }

    override fun countByAggregateType(aggregateType: String) =
        events.count { it.aggregateType == aggregateType }.toLong()

    override fun stats(): DomainEventStats {
        val byType = events.groupBy { it.aggregateType }
            .map { (type, list) -> AggregateTypeStat(type, list.size.toLong()) }
        return DomainEventStats(events.size.toLong(), byType)
    }
}
