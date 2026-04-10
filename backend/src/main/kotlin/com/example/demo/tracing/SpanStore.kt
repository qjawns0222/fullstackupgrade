package com.example.demo.tracing

interface SpanStore {
    fun save(record: SpanRecord): SpanRecord
    fun findRecent(limit: Int): List<SpanRecord>
    fun findSlowSpans(thresholdMs: Long, limit: Int): List<SpanRecord>
    fun stats(): SpanStats
}

data class SpanStats(
    val totalCount: Long,
    val slowCount: Long,
    val errorCount: Long,
    val avgDurationMs: Double
)
