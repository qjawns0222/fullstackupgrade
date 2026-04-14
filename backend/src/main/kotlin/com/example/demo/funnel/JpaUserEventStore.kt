package com.example.demo.funnel

import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class JpaUserEventStore(private val repo: UserEventRepository) : UserEventStore {

    override fun save(event: UserEvent): UserEvent = repo.save(event)

    override fun countSessionsByEventTypeSince(since: LocalDateTime): Map<String, Long> =
        repo.countSessionsByEventTypeSince(since)
            .associate { row -> row[0] as String to row[1] as Long }

    override fun countDistinctSessionsByEventTypeSince(eventType: String, since: LocalDateTime): Long =
        repo.countDistinctSessionsByEventTypeSince(eventType, since)
}
