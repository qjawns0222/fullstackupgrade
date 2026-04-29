package com.example.demo.notification

import com.example.demo.dto.NotificationMessage
import com.example.demo.entity.JobApplicationStatus
import com.example.demo.webhook.WebhookDeliveryService
import com.example.demo.webhook.WebhookEvent
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class DefaultNotificationDispatcher(
    private val stompTemplate: SimpMessagingTemplate,
    private val subscriptionService: ApplicationSubscriptionService,
    private val webhookDeliveryService: WebhookDeliveryService
) : NotificationDispatcher {

    private val log = LoggerFactory.getLogger(DefaultNotificationDispatcher::class.java)

    override fun dispatchStomp(userId: Long, event: NotificationEvent) {
        stompTemplate.convertAndSend("/topic/notifications", NotificationMessage(event.message))
    }

    override fun dispatchGraphql(userId: Long, event: NotificationEvent) {
        subscriptionService.publishNotification(
            ApplicationStatusChangedEvent(
                applicationId = event.referenceId ?: 0L,
                companyName = event.title,
                position = event.message,
                newStatus = JobApplicationStatus.APPLIED,
                userId = userId
            )
        )
    }

    override fun dispatchWebhook(userId: Long, event: NotificationEvent) {
        webhookDeliveryService.dispatch(
            WebhookEvent(
                eventType = event.type,
                payload = mapOf(
                    "title" to event.title,
                    "message" to event.message,
                    "referenceId" to event.referenceId
                ),
                userId = userId
            )
        )
    }

    override fun dispatchEmail(userId: Long, event: NotificationEvent) {
        log.info("EMAIL channel: userId=$userId title=${event.title} — deferred to JobRunr")
    }
}
