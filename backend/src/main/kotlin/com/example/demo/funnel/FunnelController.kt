package com.example.demo.funnel

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class RecordEventRequest(
    val sessionId: String,
    val userId: String? = null,
    val eventType: String,
    val resourceId: String? = null,
    val metadata: String? = null
)

@RestController
@RequestMapping("/api/funnel")
class FunnelController(private val service: FunnelAnalysisService) {

    @PostMapping("/events")
    fun recordEvent(@RequestBody req: RecordEventRequest): ResponseEntity<UserEvent> {
        val event = service.recordEvent(
            sessionId = req.sessionId,
            userId = req.userId,
            eventType = req.eventType,
            resourceId = req.resourceId,
            metadata = req.metadata
        )
        return ResponseEntity.ok(event)
    }

    @GetMapping("/stats")
    fun getStats(@RequestParam(defaultValue = "24") periodHours: Int): ResponseEntity<FunnelStats> =
        ResponseEntity.ok(service.getFunnelStats(periodHours))
}
