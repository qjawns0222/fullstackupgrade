package com.example.demo.webhook

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface WebhookEndpointRepository : JpaRepository<WebhookEndpoint, Long> {

    fun findAllByUserId(userId: Long): List<WebhookEndpoint>

    /**
     * Find all active endpoints that subscribe to the given event type.
     * Uses LIKE to match comma-separated eventTypes column.
     */
    @Query(
        """SELECT w FROM WebhookEndpoint w
           WHERE w.active = true
             AND w.user.id = :userId
             AND (w.eventTypes LIKE %:eventType%)"""
    )
    fun findActiveByUserIdAndEventType(userId: Long, eventType: String): List<WebhookEndpoint>

    @Query(
        """SELECT w FROM WebhookEndpoint w
           WHERE w.active = true
             AND (w.eventTypes LIKE %:eventType%)"""
    )
    fun findActiveByEventType(eventType: String): List<WebhookEndpoint>
}

interface WebhookDeliveryLogRepository : JpaRepository<WebhookDeliveryLog, Long> {

    fun findAllByEndpointIdOrderByCreatedAtDesc(endpointId: Long): List<WebhookDeliveryLog>

    fun findAllByEndpointUserIdOrderByCreatedAtDesc(userId: Long): List<WebhookDeliveryLog>
}
