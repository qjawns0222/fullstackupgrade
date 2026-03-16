package com.example.demo.aop

import com.example.demo.annotation.RetryOnDeadlock
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.sql.SQLException

class DeadlockRetryAspectTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var aspect: DeadlockRetryAspect
    private lateinit var joinPoint: ProceedingJoinPoint

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        aspect = DeadlockRetryAspect(meterRegistry)
        joinPoint = mock(ProceedingJoinPoint::class.java)

        val signature = mock(org.aspectj.lang.Signature::class.java)
        `when`(joinPoint.signature).thenReturn(signature)
        `when`(signature.toShortString()).thenReturn("testMethod()")
    }

    @Test
    fun `should succeed on first attempt without retry`() {
        val expectedResult = "success"
        `when`(joinPoint.proceed()).thenReturn(expectedResult)

        val result = aspect.retryOnDeadlock(joinPoint)

        assertEquals(expectedResult, result)
        assertEquals(0.0, meterRegistry.counter("database.deadlock.retry").count())
        assertEquals(0.0, meterRegistry.counter("database.deadlock.success").count())
    }

    @Test
    fun `should retry on deadlock and succeed`() {
        val deadlockException = SQLException("Deadlock found", "40001", 1213)
        val expectedResult = "success"

        `when`(joinPoint.proceed())
            .thenThrow(deadlockException)
            .thenThrow(deadlockException)
            .thenReturn(expectedResult)

        val result = aspect.retryOnDeadlock(joinPoint)

        assertEquals(expectedResult, result)
        assertEquals(2.0, meterRegistry.counter("database.deadlock.retry").count())
        assertEquals(1.0, meterRegistry.counter("database.deadlock.success").count())
    }

    @Test
    fun `should fail after max retries`() {
        val deadlockException = SQLException("Deadlock found", "40001", 1213)

        `when`(joinPoint.proceed()).thenThrow(deadlockException)

        assertThrows(SQLException::class.java) {
            aspect.retryOnDeadlock(joinPoint)
        }

        assertEquals(5.0, meterRegistry.counter("database.deadlock.retry").count())
        assertEquals(1.0, meterRegistry.counter("database.deadlock.failure").count())
    }

    @Test
    fun `should not retry non-deadlock SQLException`() {
        val nonDeadlockException = SQLException("Connection timeout", "08S01", 0)

        `when`(joinPoint.proceed()).thenThrow(nonDeadlockException)

        assertThrows(SQLException::class.java) {
            aspect.retryOnDeadlock(joinPoint)
        }

        assertEquals(0.0, meterRegistry.counter("database.deadlock.retry").count())
    }

    @Test
    fun `should detect deadlock by SQL state 40001`() {
        val deadlockException = SQLException("Transaction deadlock", "40001")
        `when`(joinPoint.proceed())
            .thenThrow(deadlockException)
            .thenReturn("success")

        aspect.retryOnDeadlock(joinPoint)

        assertTrue(meterRegistry.counter("database.deadlock.retry").count() > 0)
    }

    @Test
    fun `should detect deadlock by error code 1213`() {
        val deadlockException = SQLException("Deadlock", null, 1213)
        `when`(joinPoint.proceed())
            .thenThrow(deadlockException)
            .thenReturn("success")

        aspect.retryOnDeadlock(joinPoint)

        assertTrue(meterRegistry.counter("database.deadlock.retry").count() > 0)
    }

    @Test
    fun `should detect deadlock by message containing deadlock keyword`() {
        val deadlockException = SQLException("Lock wait timeout exceeded; try restarting transaction deadlock")
        `when`(joinPoint.proceed())
            .thenThrow(deadlockException)
            .thenReturn("success")

        aspect.retryOnDeadlock(joinPoint)

        assertTrue(meterRegistry.counter("database.deadlock.retry").count() > 0)
    }

    @Test
    fun `should propagate non-SQLException immediately`() {
        val runtimeException = RuntimeException("Test exception")

        `when`(joinPoint.proceed()).thenThrow(runtimeException)

        assertThrows(RuntimeException::class.java) {
            aspect.retryOnDeadlock(joinPoint)
        }

        assertEquals(0.0, meterRegistry.counter("database.deadlock.retry").count())
    }

    @Test
    fun `should apply exponential backoff correctly`() {
        val deadlockException = SQLException("Deadlock found", "40001", 1213)
        var attemptTimes = mutableListOf<Long>()

        `when`(joinPoint.proceed()).thenAnswer {
            attemptTimes.add(System.currentTimeMillis())
            if (attemptTimes.size < 3) {
                throw deadlockException
            }
            "success"
        }

        aspect.retryOnDeadlock(joinPoint)

        assertTrue(attemptTimes.size >= 2)
        if (attemptTimes.size >= 3) {
            val firstDelay = attemptTimes[1] - attemptTimes[0]
            val secondDelay = attemptTimes[2] - attemptTimes[1]
            assertTrue(secondDelay >= firstDelay)
        }
    }
}
