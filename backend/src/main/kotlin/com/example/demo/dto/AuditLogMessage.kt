package com.example.demo.dto

import java.time.LocalDateTime

data class AuditLogMessage(
        val userId: String,
        val action: String,
        val description: String,
        val params: String,
        val status: String,
        val errorMessage: String? = null,
        val timestamp: LocalDateTime = LocalDateTime.now()
)
