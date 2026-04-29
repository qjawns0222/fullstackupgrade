package com.example.demo.notification

interface NotificationDispatcher {
    fun dispatchStomp(userId: Long, event: NotificationEvent)
    fun dispatchGraphql(userId: Long, event: NotificationEvent)
    fun dispatchWebhook(userId: Long, event: NotificationEvent)
    fun dispatchEmail(userId: Long, event: NotificationEvent)
}
