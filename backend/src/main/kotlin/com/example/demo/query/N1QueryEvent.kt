package com.example.demo.query

import java.time.LocalDateTime

data class N1QueryEvent(
    val normalizedSql: String,
    val executionCount: Int,
    val thresholdCount: Int,
    val callerClass: String,
    val callerMethod: String,
    val detectedAt: LocalDateTime = LocalDateTime.now()
)
