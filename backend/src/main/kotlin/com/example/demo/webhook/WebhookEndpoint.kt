package com.example.demo.webhook

import com.example.demo.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Registered webhook endpoint. A user can subscribe to specific event types
 * and receive signed HTTP POST callbacks whenever those events occur.
 */
@Entity
@Table(name = "webhook_endpoints")
class WebhookEndpoint(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var targetUrl: String,

    /**
     * HMAC-SHA256 secret used to sign the payload.
     * Recipients validate the X-Webhook-Signature header.
     */
    @Column(nullable = false)
    var secret: String,

    /**
     * Comma-separated list of event types this endpoint subscribes to.
     * e.g. "APPLICATION_CREATED,APPLICATION_STATUS_CHANGED"
     */
    @Column(nullable = false)
    var eventTypes: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    var createdAt: LocalDateTime = LocalDateTime.now()
)
