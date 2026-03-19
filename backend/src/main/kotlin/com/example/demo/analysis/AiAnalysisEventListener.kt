package com.example.demo.analysis

import com.example.demo.entity.Resume
import com.example.demo.event.ResumeSearchEvent
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.UserRepository
import com.example.demo.shared.S3Service
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class AiAnalysisEventListener(
        private val repository: AnalysisRequestRepository,
        private val resumeRepository: ResumeRepository,
        private val userRepository: UserRepository,
        private val redisTemplate: StringRedisTemplate,
        @Qualifier("notificationTopic") private val topic: ChannelTopic,
        private val objectMapper: ObjectMapper,
        private val eventPublisher: ApplicationEventPublisher,
        private val s3Service: S3Service,
        private val ocrService: OcrService,
        private val messagingTemplate: SimpMessagingTemplate
) {

    private val log = LoggerFactory.getLogger(AiAnalysisEventListener::class.java)

    @org.springframework.modulith.events.ApplicationModuleListener
    fun handleAiAnalysis(event: AiAnalysisEvent) {
        val requestId = event.analysisRequestId
        val username = event.username
        log.info("Starting Async AI Analysis for Request ID: {}", requestId)

        try {
            val request =
                    repository.findById(requestId).orElseThrow {
                        RuntimeException("Analysis Request not found: $requestId")
                    }

            request.startAnalysis()
            repository.saveAndFlush(request)

            // Notify Start via WebSocket
            sendWebSocketUpdate(username, requestId, "STARTED", "Analysis started")

            var analysisResult = "No file data to process"

            if (request.fileKey != null) {
                sendWebSocketUpdate(username, requestId, "PROCESSING", "Downloading file from S3")
                val fileData = s3Service.downloadFile(request.fileKey!!)

                sendWebSocketUpdate(username, requestId, "PROCESSING", "Performing OCR analysis")
                analysisResult = ocrService.doOcr(fileData)
            }

            request.complete(analysisResult)
            repository.saveAndFlush(request)

            // Create Resume Entity
            val user =
                    userRepository.findByUsername(username).orElseThrow {
                        RuntimeException("User not found: $username")
                    }

            val resume =
                    Resume(
                            originalFileName = request.originalFileName,
                            content = request.result,
                            user = user
                    )
            resumeRepository.save(resume)
            eventPublisher.publishEvent(ResumeSearchEvent(resume.id!!))

            // Notify Completion via WebSocket
            sendWebSocketUpdate(username, requestId, "COMPLETED", "Analysis completed successfully")

            // Legacy Redis Notification
            val message: MutableMap<String, String> = HashMap()
            message["username"] = username
            message["content"] = "Analysis Completed for Request ID: $requestId"
            redisTemplate.convertAndSend(topic.topic, objectMapper.writeValueAsString(message))
        } catch (e: Exception) {
            log.error("Analysis failed for Request ID: $requestId", e)

            // Attempt to update the status in the database if possible
            try {
                repository.findById(requestId).ifPresent {
                    it.fail("Error: " + e.message)
                    repository.saveAndFlush(it)
                }
            } catch (inner: Exception) {
                log.error("Failed to save failed state to DB", inner)
            }

            sendWebSocketUpdate(username, requestId, "FAILED", e.message ?: "Unknown error")
        }
    }

    private fun sendWebSocketUpdate(
            username: String,
            requestId: Long,
            status: String,
            message: String
    ) {
        val payload =
                mapOf(
                        "requestId" to requestId,
                        "status" to status,
                        "message" to message,
                        "timestamp" to System.currentTimeMillis()
                )
        try {
            messagingTemplate.convertAndSendToUser(username, "/topic/analysis", payload)
            log.info("Sent WebSocket update to user {}: {} - {}", username, status, message)
        } catch (e: Exception) {
            log.error("Failed to send WebSocket update", e)
        }
    }
}
