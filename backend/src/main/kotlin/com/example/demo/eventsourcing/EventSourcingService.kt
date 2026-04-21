package com.example.demo.eventsourcing

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class EventSourcingService(
    private val store: DomainEventStore,
    private val objectMapper: ObjectMapper
) {
    fun record(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: Any,
        actor: String? = null
    ): DomainEvent {
        val json = objectMapper.writeValueAsString(payload)
        return store.append(
            DomainEvent(
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                eventPayload = json,
                actor = actor
            )
        )
    }

    fun replayAggregate(aggregateType: String, aggregateId: String): List<DomainEvent> =
        store.findByAggregate(aggregateType, aggregateId)

    fun recentEvents(limit: Int = 50): List<DomainEvent> =
        store.findRecent(limit)

    fun periodEvents(aggregateType: String, from: LocalDateTime, to: LocalDateTime): List<DomainEvent> =
        store.findByAggregateTypeAndPeriod(aggregateType, from, to)

    fun stats(): DomainEventStats = store.stats()
}
