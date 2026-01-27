package com.example.demo.aop

import com.example.demo.annotation.AuditLog
import com.example.demo.document.AuditLogDocument
import com.example.demo.repository.AuditLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

@ExtendWith(MockitoExtension::class)
class AuditLogAspectTest {

    @Mock private lateinit var auditLogRepository: AuditLogRepository
    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var joinPoint: ProceedingJoinPoint
    @Mock private lateinit var signature: MethodSignature
    @Mock private lateinit var securityContext: SecurityContext
    @Mock private lateinit var authentication: Authentication

    private lateinit var auditLogAspect: AuditLogAspect

    @BeforeEach
    fun setUp() {
        auditLogAspect = AuditLogAspect(auditLogRepository, objectMapper)
        SecurityContextHolder.setContext(securityContext)
    }

    @Test
    fun `handleAuditLog should save log asynchronously on success`() {
        // Given
        val auditLog = mock(AuditLog::class.java)
        `when`(auditLog.action).thenReturn("TEST_ACTION")
        `when`(auditLog.description).thenReturn("Test Description")

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(this.javaClass.methods[0]) // Just dummy
        `when`(joinPoint.args).thenReturn(arrayOf("arg1", "arg2"))
        `when`(securityContext.authentication).thenReturn(authentication)
        `when`(authentication.name).thenReturn("testuser")
        `when`(objectMapper.writeValueAsString(any())).thenReturn("[\"arg1\", \"arg2\"]")
        `when`(joinPoint.proceed()).thenReturn("Success Result")

        // When
        val result = auditLogAspect.handleAuditLog(joinPoint, auditLog)

        // Then
        assertEquals("Success Result", result)
        verify(joinPoint).proceed()

        // Asynchronous verification
        verify(auditLogRepository, timeout(1000).times(1)).save(any(AuditLogDocument::class.java))
    }

    @Test
    fun `handleAuditLog should save failure log on exception`() {
        // Given
        val auditLog = mock(AuditLog::class.java)
        `when`(auditLog.action).thenReturn("TEST_ACTION")
        `when`(auditLog.description).thenReturn("Test Description")

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(this.javaClass.methods[0])
        `when`(joinPoint.args).thenReturn(arrayOf("arg1"))
        `when`(securityContext.authentication).thenReturn(authentication)
        `when`(authentication.name).thenReturn("testuser")
        `when`(objectMapper.writeValueAsString(any())).thenReturn("[\"arg1\"]")
        `when`(joinPoint.proceed()).thenThrow(RuntimeException("Test Exception"))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            auditLogAspect.handleAuditLog(joinPoint, auditLog)
        }

        // Asynchronous verification for failure log
        verify(auditLogRepository, timeout(1000).times(1))
                .save(
                        argThat { doc ->
                            doc.status == "FAILURE" && doc.errorMessage == "Test Exception"
                        }
                )
    }
}
