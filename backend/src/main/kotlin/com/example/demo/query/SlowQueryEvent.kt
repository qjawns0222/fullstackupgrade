package com.example.demo.query

import java.time.LocalDateTime

data class SlowQueryEvent(
    val sql: String,
    val elapsedTimeMs: Long,
    val thresholdMs: Long,
    val callerClass: String,
    val callerMethod: String,
    val detectedAt: LocalDateTime = LocalDateTime.now()
)
