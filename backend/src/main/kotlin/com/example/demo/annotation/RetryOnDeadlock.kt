package com.example.demo.annotation

import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import java.sql.SQLException

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Retryable(
    retryFor = [SQLException::class],
    maxAttempts = 5,
    backoff = Backoff(
        delay = 100,
        multiplier = 2.0,
        maxDelay = 2000
    )
)
annotation class RetryOnDeadlock
