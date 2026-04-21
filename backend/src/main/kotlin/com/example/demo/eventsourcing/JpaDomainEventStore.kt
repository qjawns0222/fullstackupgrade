package com.example.demo.eventsourcing

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class JpaDomainEventStore(
    private val repo: DomainEventRepository
) : DomainEventStore {

    override fun append(event: DomainEvent) = repo.save(event)

    override fun findByAggregate(aggregateType: String, aggregateId: String): List<DomainEvent> =
        repo.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(aggregateType, aggregateId)

    override fun findRecent(limit: Int): List<DomainEvent> =
        repo.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit))

    override fun findByAggregateTypeAndPeriod(
        aggregateType: String, from: LocalDateTime, to: LocalDateTime
    ): List<DomainEvent> =
        repo.findByAggregateTypeAndOccurredAtBetweenOrderByOccurredAtAsc(aggregateType, from, to)

    override fun countByAggregateType(aggregateType: String): Long =
        repo.countByAggregateType(aggregateType)

    override fun stats(): DomainEventStats {
        val total = repo.count()
        val byType = repo.countGroupByAggregateType().map {
            AggregateTypeStat(it[0] as String, it[1] as Long)
        }
        return DomainEventStats(total, byType)
    }
}
