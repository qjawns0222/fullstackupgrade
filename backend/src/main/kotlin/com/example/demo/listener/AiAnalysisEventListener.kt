package com.example.demo.listener

import com.example.demo.entity.Resume
import com.example.demo.event.AiAnalysisEvent
import com.example.demo.event.ResumeSearchEvent
import com.example.demo.repository.AnalysisRequestRepository
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AiAnalysisEventListener(
    private val repository: AnalysisRequestRepository,
    private val resumeRepository: ResumeRepository,
    private val userRepository: UserRepository,
    private val redisTemplate: StringRedisTemplate,
    private val topic: ChannelTopic,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(AiAnalysisEventListener::class.java)

    @Async
    @EventListener
    @Transactional
    fun handleAiAnalysis(event: AiAnalysisEvent) {
        val requestId = event.analysisRequestId
        log.info("Starting Async AI Analysis for Request ID: {}", requestId)

        val request = repository.findById(requestId)
            .orElseThrow { RuntimeException("Request not found") }

        request.startAnalysis()
        repository.saveAndFlush(request) // Ensure status update is committed

        try {
            // Simulate heavy AI processing
            Thread.sleep(5000)

            val mockResult = "AI Analysis Result for " + request.originalFileName + ": Success! (Mock Data)"
            request.complete(mockResult)
            repository.saveAndFlush(request) // Save completed state
            log.info("AI Analysis Completed for Request ID: {}", requestId)

            // Create and Save Resume Entity
            val user = userRepository.findByUsername(event.username)
                .orElseThrow { RuntimeException("User not found: ${event.username}") }

            val resume = Resume(
                originalFileName = request.originalFileName,
                content = request.result,
                user = user
            )
            resumeRepository.save(resume)
            log.info("Saved Resume Entity for User: {}", event.username)

            // Publish ResumeSearchEvent with Resume ID
            eventPublisher.publishEvent(ResumeSearchEvent(resume.id!!))

            // Publish to Redis
            val message: MutableMap<String, String> = HashMap()
            message["username"] = event.username
            message["content"] = "Analysis Completed for Request ID: $requestId"

            val jsonMessage = objectMapper.writeValueAsString(message)
            redisTemplate.convertAndSend(topic.topic, jsonMessage)
            log.info("Published notification to Redis for user: {}", event.username)

        } catch (e: InterruptedException) {
            log.error("Analysis interrupted", e)
            request.fail("Analysis interrupted")
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.error("Analysis failed", e)
            request.fail("Error: " + e.message)
        }
    }
}
