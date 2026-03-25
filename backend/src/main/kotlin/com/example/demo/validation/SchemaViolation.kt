package com.example.demo.validation

import java.time.LocalDateTime

data class SchemaViolation(
    val id: String,
    val schemaPath: String,
    val endpoint: String,
    val method: String,
    val violations: List<String>,
    val requestPayload: String,
    val occurredAt: LocalDateTime = LocalDateTime.now()
)
