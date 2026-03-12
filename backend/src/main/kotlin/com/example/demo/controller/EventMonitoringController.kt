package com.example.demo.controller

import org.springframework.modulith.events.CompletedEventPublications
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/admin/events")
@PreAuthorize("hasRole('ADMIN')")
class EventMonitoringController(
    private val incomplete: IncompleteEventPublications,
    private val completed: CompletedEventPublications
) {

    @GetMapping("/incomplete")
    fun getIncompleteEvents(): List<EventPublicationResponse> {
        return incomplete.findAll().map { 
            EventPublicationResponse(
                id = it.hashCode().toString(), // Simple ID for UI
                eventType = it.event.javaClass.simpleName,
                listenerId = it.targetIdentifier.toString(),
                publicationDate = it.publicationDate,
                eventPayload = it.event.toString()
            )
        }
    }

    @GetMapping("/completed")
    fun getCompletedEvents(): List<EventPublicationResponse> {
        return completed.findAll().map {
            EventPublicationResponse(
                id = it.hashCode().toString(),
                eventType = it.event.javaClass.simpleName,
                listenerId = it.targetIdentifier.toString(),
                publicationDate = it.publicationDate,
                completionDate = it.completionDate.orElse(null),
                eventPayload = it.event.toString()
            )
        }
    }

    @PostMapping("/resubmit")
    fun resubmit(@RequestParam(defaultValue = "0") minutesAgo: Long) {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(minutesAgo))
    }

    data class EventPublicationResponse(
        val id: String,
        val eventType: String,
        val listenerId: String,
        val publicationDate: LocalDateTime,
        val completionDate: LocalDateTime? = null,
        val eventPayload: String
    )
}
