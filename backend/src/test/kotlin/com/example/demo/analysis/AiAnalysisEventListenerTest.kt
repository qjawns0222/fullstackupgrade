package com.example.demo.analysis

import com.example.demo.entity.Resume
import com.example.demo.entity.User
import com.example.demo.event.ResumeSearchEvent
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.UserRepository
import com.example.demo.shared.S3Service
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.messaging.simp.SimpMessagingTemplate

class AiAnalysisEventListenerTest {

        private lateinit var repository: AnalysisRequestRepository
        private lateinit var resumeRepository: ResumeRepository
        private lateinit var userRepository: UserRepository
        private lateinit var redisTemplate: StringRedisTemplate
        private lateinit var topic: ChannelTopic
        private lateinit var objectMapper: ObjectMapper
        private lateinit var eventPublisher: ApplicationEventPublisher
        private lateinit var s3Service: S3Service
        private lateinit var ocrService: OcrService
        private lateinit var messagingTemplate: SimpMessagingTemplate
        private lateinit var listener: AiAnalysisEventListener

        @BeforeEach
        fun setUp() {
                repository = mock(AnalysisRequestRepository::class.java)
                resumeRepository = mock(ResumeRepository::class.java)
                userRepository = mock(UserRepository::class.java)
                redisTemplate = mock(StringRedisTemplate::class.java)
                topic = mock(ChannelTopic::class.java)
                objectMapper = mock(ObjectMapper::class.java)
                eventPublisher = mock(ApplicationEventPublisher::class.java)
                s3Service = mock(S3Service::class.java)
                ocrService = mock(OcrService::class.java)
                messagingTemplate = mock(SimpMessagingTemplate::class.java)

                listener =
                        AiAnalysisEventListener(
                                repository,
                                resumeRepository,
                                userRepository,
                                redisTemplate,
                                topic,
                                objectMapper,
                                eventPublisher,
                                s3Service,
                                ocrService,
                                messagingTemplate
                        )
        }

        private fun <T> anyNonNull(): T {
                any<T>()
                @Suppress("UNCHECKED_CAST")
                return "" as T // Use dummy small value to satisfy non-null check
        }

        @Test
        fun `should send websocket updates during analysis lifecycle`() {
                // Given
                val requestId = 1L
                val username = "testuser"
                val event = AiAnalysisEvent(requestId, username)
                val request = AnalysisRequest("test.pdf", "keys/test.pdf").apply { id = requestId }
                val mockUser = mock(User::class.java)

                `when`(repository.findById(requestId)).thenReturn(Optional.of(request))
                `when`(s3Service.downloadFile(anyString())).thenReturn(ByteArray(10))

                // Use a dummy non-null value for the matcher to satisfy Kotlin's check
                `when`(ocrService.doOcr(any(ByteArray::class.java) ?: ByteArray(0)))
                        .thenReturn("OCR Result")

                `when`(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser))
                `when`(topic.topic).thenReturn("test-topic")

                // Stub save to populate ID to avoid NPE at resume.id!!
                `when`(resumeRepository.save(any(Resume::class.java) ?: Resume("", null, mockUser)))
                        .thenAnswer {
                                val resume = it.arguments[0] as Resume
                                resume.id = 123L
                                resume
                        }

                // When
                listener.handleAiAnalysis(event)

                // Then
                // 1. STARTED
                verify(messagingTemplate)
                        .convertAndSendToUser(
                                eq(username),
                                eq("/topic/analysis"),
                                argThat { (it as Map<*, *>)["status"] == "STARTED" }
                        )

                // 2. PROCESSING (at least once)
                verify(messagingTemplate, atLeastOnce())
                        .convertAndSendToUser(
                                eq(username),
                                eq("/topic/analysis"),
                                argThat { (it as Map<*, *>)["status"] == "PROCESSING" }
                        )

                // 3. COMPLETED
                verify(messagingTemplate)
                        .convertAndSendToUser(
                                eq(username),
                                eq("/topic/analysis"),
                                argThat { (it as Map<*, *>)["status"] == "COMPLETED" }
                        )

                verify(resumeRepository).save(any(Resume::class.java) ?: Resume("", null, mockUser))
                verify(eventPublisher)
                        .publishEvent(any(ResumeSearchEvent::class.java) ?: ResumeSearchEvent(0L))
        }

        @Test
        fun `should send FAILED websocket update on error`() {
                // Given
                val requestId = 1L
                val username = "testuser"
                val event = AiAnalysisEvent(requestId, username)
                `when`(repository.findById(requestId)).thenThrow(RuntimeException("Database error"))

                // When
                listener.handleAiAnalysis(event)

                // Then
                verify(messagingTemplate)
                        .convertAndSendToUser(
                                eq(username),
                                eq("/topic/analysis"),
                                argThat { (it as Map<*, *>)["status"] == "FAILED" }
                        )
        }
}
