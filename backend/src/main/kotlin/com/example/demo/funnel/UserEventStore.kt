package com.example.demo.funnel

import java.time.LocalDateTime

interface UserEventStore {
    fun save(event: UserEvent): UserEvent
    fun countSessionsByEventTypeSince(since: LocalDateTime): Map<String, Long>
    fun countDistinctSessionsByEventTypeSince(eventType: String, since: LocalDateTime): Long
}
