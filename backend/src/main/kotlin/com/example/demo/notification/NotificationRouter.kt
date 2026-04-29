package com.example.demo.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationRouter(
    private val preferenceStore: NotificationPreferenceStore,
    private val dispatcher: NotificationDispatcher
) {
    private val log = LoggerFactory.getLogger(NotificationRouter::class.java)

    fun route(userId: Long, event: NotificationEvent) {
        val enabled = preferenceStore.findByUserId(userId)
            .filter { it.enabled }
            .map { it.channel }
            .toSet()
            .ifEmpty { setOf(NotificationChannel.STOMP) }

        enabled.forEach { channel ->
            runCatching { dispatch(channel, userId, event) }
                .onFailure { log.warn("Dispatch failed [channel=$channel userId=$userId]: ${it.message}") }
        }
    }

    private fun dispatch(channel: NotificationChannel, userId: Long, event: NotificationEvent) =
        when (channel) {
            NotificationChannel.STOMP   -> dispatcher.dispatchStomp(userId, event)
            NotificationChannel.GRAPHQL -> dispatcher.dispatchGraphql(userId, event)
            NotificationChannel.WEBHOOK -> dispatcher.dispatchWebhook(userId, event)
            NotificationChannel.EMAIL   -> dispatcher.dispatchEmail(userId, event)
        }
}
