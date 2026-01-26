package com.example.demo.aop

import com.example.demo.annotation.Idempotent
import com.example.demo.exception.IdempotencyException
import jakarta.servlet.http.HttpServletRequest
import java.util.concurrent.TimeUnit
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@ExtendWith(MockitoExtension::class)
class IdempotencyAspectTest {

    @Mock lateinit var redisTemplate: StringRedisTemplate

    @Mock lateinit var valueOperations: ValueOperations<String, String>

    @Mock lateinit var joinPoint: ProceedingJoinPoint

    @Mock lateinit var request: HttpServletRequest

    @InjectMocks lateinit var aspect: IdempotencyAspect

    @Test
    fun `should throw exception when idempotency key is missing`() {
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)

        val annotation = mock(Idempotent::class.java)
        `when`(annotation.keyHeader).thenReturn("Idempotency-Key")
        `when`(request.getHeader("Idempotency-Key")).thenReturn(null)

        assertThrows(IdempotencyException::class.java) {
            aspect.handleIdempotency(joinPoint, annotation)
        }
    }

    @Test
    fun `should throw exception when key exists in redis`() {
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)

        val annotation = mock(Idempotent::class.java)
        `when`(annotation.keyHeader).thenReturn("Idempotency-Key")
        `when`(request.getHeader("Idempotency-Key")).thenReturn("test-key")

        `when`(redisTemplate.hasKey("idempotency:test-key")).thenReturn(true)

        assertThrows(IdempotencyException::class.java) {
            aspect.handleIdempotency(joinPoint, annotation)
        }
    }

    @Test
    fun `should proceed when key does not exist`() {
        val attributes = ServletRequestAttributes(request)
        RequestContextHolder.setRequestAttributes(attributes)

        val annotation = mock(Idempotent::class.java)
        `when`(annotation.keyHeader).thenReturn("Idempotency-Key")
        `when`(annotation.expireTime).thenReturn(60)
        `when`(annotation.timeUnit).thenReturn(TimeUnit.SECONDS)

        `when`(request.getHeader("Idempotency-Key")).thenReturn("unique-key")
        `when`(redisTemplate.hasKey("idempotency:unique-key")).thenReturn(false)
        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)

        aspect.handleIdempotency(joinPoint, annotation)

        verify(valueOperations).set("idempotency:unique-key", "PROCESSING", 60, TimeUnit.SECONDS)
        verify(joinPoint).proceed()
    }
}
