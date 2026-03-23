package com.example.demo.webhook

import com.example.demo.repository.UserRepository
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks")
class WebhookController(
    private val webhookDeliveryService: WebhookDeliveryService,
    private val userRepository: UserRepository
) {

    private fun resolveUserId(authentication: Authentication): Long {
        val user = userRepository.findByUsername(authentication.name).orElseThrow {
            IllegalArgumentException("User not found")
        }
        return user.id!!
    }

    @PostMapping("/endpoints")
    fun register(
        @Valid @RequestBody request: WebhookEndpointRequest,
        authentication: Authentication
    ): ResponseEntity<WebhookEndpointResponse> {
        val user = userRepository.findByUsername(authentication.name).orElseThrow {
            IllegalArgumentException("User not found")
        }
        val endpoint = webhookDeliveryService.registerEndpoint(user.id!!, request, user)
        return ResponseEntity.ok(endpoint.toResponse())
    }

    @DeleteMapping("/endpoints/{id}")
    fun deactivate(
        @PathVariable id: Long,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val userId = resolveUserId(authentication)
        webhookDeliveryService.deactivateEndpoint(id, userId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/endpoints")
    fun listEndpoints(authentication: Authentication): ResponseEntity<List<WebhookEndpointResponse>> {
        val userId = resolveUserId(authentication)
        return ResponseEntity.ok(webhookDeliveryService.getEndpoints(userId))
    }

    @GetMapping("/deliveries")
    fun listDeliveries(authentication: Authentication): ResponseEntity<List<WebhookDeliveryLogResponse>> {
        val userId = resolveUserId(authentication)
        return ResponseEntity.ok(webhookDeliveryService.getDeliveryLogs(userId))
    }

    /**
     * Test endpoint: fires a manual webhook event for the current user's registered endpoints.
     * Useful for verifying an endpoint is reachable before relying on it in production.
     */
    @PostMapping("/test")
    fun testFire(
        @RequestParam eventType: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, String>> {
        val userId = resolveUserId(authentication)
        webhookDeliveryService.dispatch(
            WebhookEvent(
                eventType = eventType,
                payload = mapOf("message" to "test event", "source" to "manual"),
                userId = userId
            )
        )
        return ResponseEntity.ok(mapOf("status" to "dispatched", "eventType" to eventType))
    }
}
