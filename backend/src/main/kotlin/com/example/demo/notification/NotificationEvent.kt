package com.example.demo.notification

data class NotificationEvent(
    val type: String,
    val title: String,
    val message: String,
    val referenceId: Long? = null
)
