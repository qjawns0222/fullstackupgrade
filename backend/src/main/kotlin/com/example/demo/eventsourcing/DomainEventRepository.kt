package com.example.demo.eventsourcing

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface DomainEventRepository : JpaRepository<DomainEvent, Long> {
    fun findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
        aggregateType: String, aggregateId: String
    ): List<DomainEvent>

    fun findAllByOrderByOccurredAtDesc(pageable: Pageable): List<DomainEvent>

    fun findByAggregateTypeAndOccurredAtBetweenOrderByOccurredAtAsc(
        aggregateType: String, from: LocalDateTime, to: LocalDateTime
    ): List<DomainEvent>

    fun countByAggregateType(aggregateType: String): Long

    @Query("SELECT e.aggregateType, COUNT(e) FROM DomainEvent e GROUP BY e.aggregateType ORDER BY COUNT(e) DESC")
    fun countGroupByAggregateType(): List<Array<Any>>
}
