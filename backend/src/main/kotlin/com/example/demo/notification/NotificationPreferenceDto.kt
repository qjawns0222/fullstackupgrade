package com.example.demo.notification

import java.time.LocalDateTime

data class NotificationPreferenceResponse(
    val channel: NotificationChannel,
    val enabled: Boolean,
    val updatedAt: LocalDateTime
)

data class NotificationPreferenceRequest(
    val channel: NotificationChannel,
    val enabled: Boolean
)
