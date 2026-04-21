package com.example.demo.eventsourcing

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "domain_events")
class DomainEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "aggregate_type", nullable = false)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    val eventPayload: String,

    @Column(name = "actor")
    val actor: String? = null,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: LocalDateTime = LocalDateTime.now()
)
