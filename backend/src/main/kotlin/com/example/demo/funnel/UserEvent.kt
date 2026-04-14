package com.example.demo.funnel

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_events")
data class UserEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "session_id", nullable = false, length = 100)
    val sessionId: String,

    @Column(name = "user_id", length = 100)
    val userId: String? = null,

    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,

    @Column(name = "resource_id", length = 200)
    val resourceId: String? = null,

    @Column(name = "metadata", columnDefinition = "TEXT")
    val metadata: String? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime = LocalDateTime.now()
)
