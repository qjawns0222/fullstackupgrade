package com.example.demo.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Core delivery engine.
 *
 * Responsibilities:
 * - Find all active endpoints subscribed to a given event type
 * - Serialize the payload to JSON
 * - Sign the payload with HMAC-SHA256 using each endpoint's secret
 * - POST to the target URL via OkHttp3 with a 10-second timeout
 * - Persist a WebhookDeliveryLog for every attempt (success or failure)
 * - Retry up to MAX_RETRIES times with exponential back-off (blocking sleep in async thread)
 */
@Service
class WebhookDeliveryService(
    private val endpointRepository: WebhookEndpointRepository,
    private val deliveryLogRepository: WebhookDeliveryLogRepository,
    private val okHttpClient: OkHttpClient,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(WebhookDeliveryService::class.java)

    companion object {
        private const val MAX_RETRIES = 3
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Publishes an event to all active endpoints subscribed to [eventType].
     * Executed asynchronously so the caller is never blocked by outbound HTTP.
     */
    @Async("webhookExecutor")
    fun dispatch(event: WebhookEvent) {
        val endpoints = endpointRepository.findActiveByEventType(event.eventType)
        if (endpoints.isEmpty()) return

        val payloadJson = objectMapper.writeValueAsString(
            mapOf(
                "eventType" to event.eventType,
                "userId" to event.userId,
                "timestamp" to LocalDateTime.now().toString(),
                "data" to event.payload
            )
        )

        endpoints.forEach { endpoint ->
            deliverWithRetry(endpoint, event.eventType, payloadJson)
        }
    }

    private fun deliverWithRetry(
        endpoint: WebhookEndpoint,
        eventType: String,
        payloadJson: String
    ) {
        val log = deliveryLogRepository.save(
            WebhookDeliveryLog(
                endpoint = endpoint,
                eventType = eventType,
                payload = payloadJson,
                status = DeliveryStatus.PENDING
            )
        )

        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            log.attemptCount = attempt
            try {
                val result = doPost(endpoint, payloadJson)
                log.httpStatus = result.first
                log.responseBody = result.second?.take(1000)
                log.status = if (result.first in 200..299) DeliveryStatus.SUCCESS else DeliveryStatus.FAILED
                log.deliveredAt = LocalDateTime.now()
                deliveryLogRepository.save(log)

                if (log.status == DeliveryStatus.SUCCESS) return
            } catch (e: Exception) {
                lastException = e
                log.status = DeliveryStatus.FAILED
                log.responseBody = e.message?.take(500)
                deliveryLogRepository.save(log)
                this.log.warn("Webhook delivery attempt $attempt failed for endpoint ${endpoint.id}: ${e.message}")
            }

            // Exponential back-off: 1s, 2s, 4s
            if (attempt < MAX_RETRIES) {
                Thread.sleep(1000L * (1 shl (attempt - 1)))
            }
        }

        this.log.error(
            "Webhook delivery permanently failed for endpoint ${endpoint.id} after $MAX_RETRIES attempts",
            lastException
        )
    }

    private fun doPost(endpoint: WebhookEndpoint, payloadJson: String): Pair<Int, String?> {
        val signature = WebhookSignatureUtil.sign(payloadJson, endpoint.secret)
        val requestBody = payloadJson.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(endpoint.targetUrl)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("X-Webhook-Signature", signature)
            .header("X-Webhook-Event", endpoint.eventTypes)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            return Pair(response.code, response.body?.string())
        }
    }

    @Transactional
    fun registerEndpoint(userId: Long, request: WebhookEndpointRequest, user: com.example.demo.entity.User): WebhookEndpoint {
        val endpoint = WebhookEndpoint(
            targetUrl = request.targetUrl,
            secret = request.secret,
            eventTypes = request.eventTypes,
            active = request.active,
            user = user
        )
        return endpointRepository.save(endpoint)
    }

    @Transactional
    fun deactivateEndpoint(id: Long, userId: Long) {
        val endpoint = endpointRepository.findById(id).orElseThrow {
            IllegalArgumentException("Webhook endpoint not found: $id")
        }
        if (endpoint.user.id != userId) throw IllegalArgumentException("Unauthorized")
        endpoint.active = false
        endpointRepository.save(endpoint)
    }

    @Transactional(readOnly = true)
    fun getEndpoints(userId: Long): List<WebhookEndpointResponse> {
        return endpointRepository.findAllByUserId(userId).map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getDeliveryLogs(userId: Long): List<WebhookDeliveryLogResponse> {
        return deliveryLogRepository.findAllByEndpointUserIdOrderByCreatedAtDesc(userId).map { it.toResponse() }
    }
}

fun WebhookEndpoint.toResponse() = WebhookEndpointResponse(
    id = id!!,
    targetUrl = targetUrl,
    eventTypes = eventTypes,
    active = active,
    createdAt = createdAt
)

fun WebhookDeliveryLog.toResponse() = WebhookDeliveryLogResponse(
    id = id!!,
    endpointId = endpoint.id!!,
    eventType = eventType,
    payload = payload,
    status = status,
    httpStatus = httpStatus,
    responseBody = responseBody,
    attemptCount = attemptCount,
    deliveredAt = deliveredAt,
    createdAt = createdAt
)
