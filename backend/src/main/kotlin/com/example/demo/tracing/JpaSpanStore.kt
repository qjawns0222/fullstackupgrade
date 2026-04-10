package com.example.demo.tracing

import org.springframework.stereotype.Component

@Component
class JpaSpanStore(private val repo: SpanRecordRepository) : SpanStore {

    override fun save(record: SpanRecord) = repo.save(record)

    override fun findRecent(limit: Int): List<SpanRecord> =
        repo.findTop100ByOrderByRecordedAtDesc().take(limit)

    override fun findSlowSpans(thresholdMs: Long, limit: Int): List<SpanRecord> =
        repo.findByDurationMsGreaterThanOrderByDurationMsDesc(thresholdMs).take(limit)

    override fun stats(): SpanStats = SpanStats(
        totalCount = repo.count(),
        slowCount = repo.countByStatus("SLOW"),
        errorCount = repo.countByStatus("ERROR"),
        avgDurationMs = repo.avgDurationMs()
    )
}
