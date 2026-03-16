package com.example.demo.aop

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.SQLException

@Aspect
@Component
class DeadlockRetryAspect(
    private val meterRegistry: MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val deadlockCounter: Counter = Counter.builder("database.deadlock.retry")
        .description("Number of deadlock retry attempts")
        .register(meterRegistry)

    private val deadlockSuccessCounter: Counter = Counter.builder("database.deadlock.success")
        .description("Number of successful deadlock recoveries")
        .register(meterRegistry)

    private val deadlockFailureCounter: Counter = Counter.builder("database.deadlock.failure")
        .description("Number of failed deadlock recoveries after all retries")
        .register(meterRegistry)

    @Around("@annotation(com.example.demo.annotation.RetryOnDeadlock)")
    fun retryOnDeadlock(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.toShortString()
        var attempt = 1
        var lastException: SQLException? = null

        while (attempt <= 5) {
            try {
                val result = joinPoint.proceed()

                if (attempt > 1) {
                    logger.info("Deadlock recovered after {} attempts for method: {}", attempt, methodName)
                    deadlockSuccessCounter.increment()
                }

                return result

            } catch (ex: SQLException) {
                if (isDeadlockException(ex)) {
                    lastException = ex
                    deadlockCounter.increment()

                    logger.warn(
                        "Deadlock detected on attempt {} for method: {}. SQL State: {}, Error Code: {}",
                        attempt, methodName, ex.sqlState, ex.errorCode
                    )

                    if (attempt >= 5) {
                        deadlockFailureCounter.increment()
                        logger.error("Deadlock not resolved after {} attempts for method: {}", attempt, methodName)
                        throw ex
                    }

                    val backoffTime = calculateBackoff(attempt)
                    logger.debug("Retrying in {}ms...", backoffTime)
                    Thread.sleep(backoffTime)

                    attempt++
                } else {
                    throw ex
                }
            } catch (ex: Throwable) {
                throw ex
            }
        }

        throw lastException ?: SQLException("Unexpected deadlock retry failure")
    }

    private fun isDeadlockException(ex: SQLException): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is SQLException) {
                val sqlState = current.sqlState
                val errorCode = current.errorCode

                if (sqlState == "40001" ||
                    sqlState == "41000" ||
                    errorCode == 1213 ||
                    errorCode == 1205 ||
                    current.message?.contains("deadlock", ignoreCase = true) == true ||
                    current.message?.contains("lock wait timeout", ignoreCase = true) == true) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    private fun calculateBackoff(attempt: Int): Long {
        val baseDelay = 100L
        val multiplier = 2.0
        val maxDelay = 2000L

        val calculatedDelay = (baseDelay * Math.pow(multiplier, (attempt - 1).toDouble())).toLong()
        return minOf(calculatedDelay, maxDelay)
    }
}
