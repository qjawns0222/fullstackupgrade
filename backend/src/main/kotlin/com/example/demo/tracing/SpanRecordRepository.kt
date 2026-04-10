package com.example.demo.tracing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SpanRecordRepository : JpaRepository<SpanRecord, Long> {
    fun findTop100ByOrderByRecordedAtDesc(): List<SpanRecord>
    fun findByDurationMsGreaterThanOrderByDurationMsDesc(thresholdMs: Long): List<SpanRecord>
    fun countByStatus(status: String): Long

    @Query("SELECT COALESCE(AVG(s.durationMs), 0.0) FROM SpanRecord s")
    fun avgDurationMs(): Double
}
