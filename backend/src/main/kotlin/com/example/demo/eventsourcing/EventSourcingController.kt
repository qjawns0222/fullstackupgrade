package com.example.demo.eventsourcing

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/event-sourcing")
class EventSourcingController(private val service: EventSourcingService) {

    @GetMapping("/stats")
    fun stats() = service.stats()

    @GetMapping("/recent")
    fun recent(@RequestParam(defaultValue = "50") limit: Int) =
        service.recentEvents(limit).map { it.toDto() }

    @GetMapping("/aggregate/{type}/{id}")
    fun replay(
        @PathVariable type: String,
        @PathVariable id: String
    ) = service.replayAggregate(type, id).map { it.toDto() }

    @GetMapping("/aggregate/{type}/period")
    fun period(
        @PathVariable type: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime
    ) = service.periodEvents(type, from, to).map { it.toDto() }

    @PostMapping("/record")
    fun record(@RequestBody req: RecordRequest): DomainEventDto {
        val event = service.record(
            aggregateType = req.aggregateType,
            aggregateId = req.aggregateId,
            eventType = req.eventType,
            payload = req.payload,
            actor = req.actor
        )
        return event.toDto()
    }
}

data class RecordRequest(
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: Map<String, Any> = emptyMap(),
    val actor: String? = null
)

data class DomainEventDto(
    val id: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val eventPayload: String,
    val actor: String?,
    val occurredAt: String
)

fun DomainEvent.toDto() = DomainEventDto(
    id = id,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    eventPayload = eventPayload,
    actor = actor,
    occurredAt = occurredAt.toString()
)
