package com.example.demo.sanitization

import com.example.demo.annotation.Sanitize
import com.example.demo.annotation.SanitizePolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Annotation source for tests — avoids Mockito annotation mocking issues with Kotlin.
 */
object SanitizeAnnotationHolder {
    @Sanitize(policy = SanitizePolicy.RESUME)
    fun resumeMethod() {}

    @Sanitize(policy = SanitizePolicy.PLAIN_TEXT)
    fun plainTextMethod() {}

    fun getResumeAnnotation(): Sanitize =
        this::class.java.getMethod("resumeMethod").getAnnotation(Sanitize::class.java)

    fun getPlainTextAnnotation(): Sanitize =
        this::class.java.getMethod("plainTextMethod").getAnnotation(Sanitize::class.java)
}

class SanitizeAspectTest {

    private lateinit var sanitizerService: HtmlSanitizerService
    private lateinit var aspect: SanitizeAspect
    private lateinit var joinPoint: ProceedingJoinPoint
    private lateinit var signature: MethodSignature
    private lateinit var method: Method
    private lateinit var param: Parameter

    @BeforeEach
    fun setUp() {
        val registry = SimpleMeterRegistry()
        sanitizerService = HtmlSanitizerService(
            PlainTextSanitizationPolicy(),
            ResumeSanitizationPolicy(),
            RichTextSanitizationPolicy(),
            registry
        )
        val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
        aspect = SanitizeAspect(sanitizerService, objectMapper)

        joinPoint = mock(ProceedingJoinPoint::class.java)
        signature = mock(MethodSignature::class.java)
        method = mock(Method::class.java)
        param = mock(Parameter::class.java)

        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.signature).thenReturn(signature)
        `when`(param.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody::class.java))
            .thenReturn(false)
        `when`(method.parameters).thenReturn(arrayOf(param))
    }

    @Test
    fun `sanitizes String argument with RESUME policy`() {
        val malicious = "<p>이름</p><script>alert(1)</script>"
        `when`(joinPoint.args).thenReturn(arrayOf(malicious))
        `when`(joinPoint.proceed(any())).thenReturn(Unit)

        aspect.sanitizeArguments(joinPoint, SanitizeAnnotationHolder.getResumeAnnotation())

        val captor = argumentCaptor<Array<Any?>>()
        verify(joinPoint).proceed(captor.capture())
        val sanitizedArg = captor.value[0] as String
        assertFalse(sanitizedArg.contains("<script>"), "script must be removed")
        assertTrue(sanitizedArg.contains("이름"), "safe content preserved")
    }

    @Test
    fun `sanitizes String argument with PLAIN_TEXT policy`() {
        val input = "<b>홍길동</b>"
        `when`(joinPoint.args).thenReturn(arrayOf(input))
        `when`(joinPoint.proceed(any())).thenReturn(Unit)

        aspect.sanitizeArguments(joinPoint, SanitizeAnnotationHolder.getPlainTextAnnotation())

        val captor = argumentCaptor<Array<Any?>>()
        verify(joinPoint).proceed(captor.capture())
        val result = captor.value[0] as String
        assertFalse(result.contains("<b>"), "bold tag must be stripped in PLAIN_TEXT mode")
    }

    @Test
    fun `non-String argument is passed through unchanged`() {
        val nonString = 42L
        `when`(joinPoint.args).thenReturn(arrayOf(nonString))
        `when`(joinPoint.proceed(any())).thenReturn(Unit)

        aspect.sanitizeArguments(joinPoint, SanitizeAnnotationHolder.getResumeAnnotation())

        val captor = argumentCaptor<Array<Any?>>()
        verify(joinPoint).proceed(captor.capture())
        assertEquals(42L, captor.value[0], "non-String argument must pass through unchanged")
    }

    @Test
    fun `null argument is passed through unchanged`() {
        `when`(joinPoint.args).thenReturn(arrayOf(null))
        `when`(method.parameters).thenReturn(arrayOf(param))
        `when`(joinPoint.proceed(any())).thenReturn(Unit)

        assertDoesNotThrow {
            aspect.sanitizeArguments(joinPoint, SanitizeAnnotationHolder.getResumeAnnotation())
        }
        verify(joinPoint).proceed(any())
    }

    // Kotlin-safe argument captor helper
    private fun <T> argumentCaptor(): org.mockito.ArgumentCaptor<T> {
        @Suppress("UNCHECKED_CAST")
        return org.mockito.ArgumentCaptor.forClass(Any::class.java) as org.mockito.ArgumentCaptor<T>
    }

    private fun <T> any(): T {
        org.mockito.Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }
}
