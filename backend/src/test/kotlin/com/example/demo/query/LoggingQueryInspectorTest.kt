package com.example.demo.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class LoggingQueryInspectorTest {

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var inspector: LoggingQueryInspector

    @BeforeEach
    fun setUp() {
        inspector = LoggingQueryInspector(eventPublisher)
    }

    @Test
    fun `onSlowQuery publishes SlowQueryEvent to ApplicationEventPublisher`() {
        val event = SlowQueryEvent(
            sql = "SELECT * FROM users",
            elapsedTimeMs = 500L,
            thresholdMs = 300L,
            callerClass = "UserService",
            callerMethod = "findAll",
            detectedAt = LocalDateTime.now()
        )

        inspector.onSlowQuery(event)

        verify(eventPublisher).publishEvent(event)
    }

    @Test
    fun `onN1Detected publishes N1QueryEvent to ApplicationEventPublisher`() {
        val event = N1QueryEvent(
            normalizedSql = "SELECT * FROM job_applications WHERE user_id = ?",
            executionCount = 5,
            thresholdCount = 5,
            callerClass = "JobApplicationService",
            callerMethod = "getAllApplications",
            detectedAt = LocalDateTime.now()
        )

        inspector.onN1Detected(event)

        verify(eventPublisher).publishEvent(event)
    }
}
