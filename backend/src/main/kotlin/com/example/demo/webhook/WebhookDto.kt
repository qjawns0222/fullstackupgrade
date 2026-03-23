package com.example.demo.webhook

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime

data class WebhookEndpointRequest(
    @field:NotBlank(message = "targetUrl must not be blank")
    val targetUrl: String,

    @field:NotBlank(message = "secret must not be blank")
    val secret: String,

    @field:NotBlank(message = "eventTypes must not be blank")
    val eventTypes: String,

    val active: Boolean = true
)

data class WebhookEndpointResponse(
    val id: Long,
    val targetUrl: String,
    val eventTypes: String,
    val active: Boolean,
    val createdAt: LocalDateTime
)

data class WebhookDeliveryLogResponse(
    val id: Long,
    val endpointId: Long,
    val eventType: String,
    val payload: String,
    val status: DeliveryStatus,
    val httpStatus: Int?,
    val responseBody: String?,
    val attemptCount: Int,
    val deliveredAt: LocalDateTime?,
    val createdAt: LocalDateTime
)

/** Internal event published to trigger webhook delivery */
data class WebhookEvent(
    val eventType: String,
    val payload: Map<String, Any?>,
    val userId: Long
)
