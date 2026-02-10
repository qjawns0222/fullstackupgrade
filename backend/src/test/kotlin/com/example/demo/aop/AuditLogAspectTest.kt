package com.example.demo.aop

import com.example.demo.annotation.AuditLog
import com.example.demo.dto.AuditLogMessage
import com.example.demo.service.AuditLogProducer
import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

@ExtendWith(MockitoExtension::class)
class AuditLogAspectTest {

    @Mock private lateinit var auditLogProducer: AuditLogProducer
    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var joinPoint: ProceedingJoinPoint
    @Mock private lateinit var signature: MethodSignature
    @Mock private lateinit var securityContext: SecurityContext
    @Mock private lateinit var authentication: Authentication

    private lateinit var auditLogAspect: AuditLogAspect

    @BeforeEach
    fun setUp() {
        auditLogAspect = AuditLogAspect(auditLogProducer, objectMapper)
        SecurityContextHolder.setContext(securityContext)
    }

    private fun <T> safeAny(type: Class<T>): T = ArgumentMatchers.any(type)

    @Test
    fun `handleAuditLog should send log message on success`() {
        // Given
        val auditLog = mock(AuditLog::class.java)
        `when`(auditLog.action).thenReturn("TEST_ACTION")
        `when`(auditLog.description).thenReturn("Test Description")

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(this.javaClass.methods[0]) // Just dummy
        `when`(joinPoint.args).thenReturn(arrayOf("arg1", "arg2"))
        `when`(securityContext.authentication).thenReturn(authentication)
        `when`(authentication.name).thenReturn("testuser")
        `when`(objectMapper.writeValueAsString(safeAny(Object::class.java)))
                .thenReturn("[\"arg1\", \"arg2\"]")
        `when`(joinPoint.proceed()).thenReturn("Success Result")

        // When
        val result = auditLogAspect.handleAuditLog(joinPoint, auditLog)

        // Then
        assertEquals("Success Result", result)
        verify(joinPoint).proceed()

        // Verify producer called
        verify(auditLogProducer, times(1)).sendAuditLog(safeAny(AuditLogMessage::class.java))
    }

    @Test
    fun `handleAuditLog should send failure log on exception`() {
        // Given
        val auditLog = mock(AuditLog::class.java)
        `when`(auditLog.action).thenReturn("TEST_ACTION")
        `when`(auditLog.description).thenReturn("Test Description")

        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.method).thenReturn(this.javaClass.methods[0])
        `when`(joinPoint.args).thenReturn(arrayOf("arg1"))
        `when`(securityContext.authentication).thenReturn(authentication)
        `when`(authentication.name).thenReturn("testuser")
        `when`(objectMapper.writeValueAsString(safeAny(Object::class.java)))
                .thenReturn("[\"arg1\"]")
        `when`(joinPoint.proceed()).thenThrow(RuntimeException("Test Exception"))

        // Setup capture via doAnswer before execution
        val capturedMessage = arrayOfNulls<AuditLogMessage>(1)
        doAnswer { invocation ->
                    capturedMessage[0] = invocation.getArgument(0)
                    null
                }
                .`when`(auditLogProducer)
                .sendAuditLog(safeAny(AuditLogMessage::class.java))

        // When & Then
        assertThrows(RuntimeException::class.java) {
            auditLogAspect.handleAuditLog(joinPoint, auditLog)
        }

        // Verify
        val message = capturedMessage[0]
        assertNotNull(message)
        assertEquals("FAILURE", message!!.status)
        assertEquals("Test Exception", message.errorMessage)
    }
}
