package com.example.demo.funnel

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface UserEventRepository : JpaRepository<UserEvent, Long> {
    fun countByEventType(eventType: String): Long

    @Query("""
        SELECT u.eventType, COUNT(DISTINCT u.sessionId)
        FROM UserEvent u
        WHERE u.occurredAt >= :since
        GROUP BY u.eventType
    """)
    fun countSessionsByEventTypeSince(since: LocalDateTime): List<Array<Any>>

    @Query("""
        SELECT COUNT(DISTINCT u.sessionId)
        FROM UserEvent u
        WHERE u.eventType = :eventType
          AND u.occurredAt >= :since
    """)
    fun countDistinctSessionsByEventTypeSince(eventType: String, since: LocalDateTime): Long
}
