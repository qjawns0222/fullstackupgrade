package com.example.demo.webhook

import jakarta.persistence.*
import java.time.LocalDateTime

enum class DeliveryStatus {
    SUCCESS, FAILED, PENDING
}

/**
 * Immutable record of every outbound webhook delivery attempt.
 * Keeps a full audit trail: request payload, HTTP status, response body, retry count.
 */
@Entity
@Table(name = "webhook_delivery_logs")
class WebhookDeliveryLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    var endpoint: WebhookEndpoint,

    @Column(nullable = false)
    var eventType: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DeliveryStatus = DeliveryStatus.PENDING,

    var httpStatus: Int? = null,

    @Column(columnDefinition = "TEXT")
    var responseBody: String? = null,

    var attemptCount: Int = 0,

    var deliveredAt: LocalDateTime? = null,

    var createdAt: LocalDateTime = LocalDateTime.now()
)
