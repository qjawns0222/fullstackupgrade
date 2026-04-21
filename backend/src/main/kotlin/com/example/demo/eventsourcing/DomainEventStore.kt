package com.example.demo.eventsourcing

import java.time.LocalDateTime

interface DomainEventStore {
    fun append(event: DomainEvent): DomainEvent
    fun findByAggregate(aggregateType: String, aggregateId: String): List<DomainEvent>
    fun findRecent(limit: Int): List<DomainEvent>
    fun findByAggregateTypeAndPeriod(aggregateType: String, from: LocalDateTime, to: LocalDateTime): List<DomainEvent>
    fun countByAggregateType(aggregateType: String): Long
    fun stats(): DomainEventStats
}

data class DomainEventStats(
    val totalEvents: Long,
    val aggregateTypes: List<AggregateTypeStat>
)

data class AggregateTypeStat(
    val aggregateType: String,
    val count: Long
)
