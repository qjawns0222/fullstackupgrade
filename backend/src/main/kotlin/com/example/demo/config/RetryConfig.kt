package com.example.demo.config

import org.springframework.classify.Classifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.RetryPolicy
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import java.sql.SQLException

@Configuration
@EnableRetry
class RetryConfig {

    @Bean
    fun retryTemplate(): RetryTemplate {
        return RetryTemplate.builder()
            .customPolicy(deadlockRetryPolicy())
            .exponentialBackoff(100, 2.0, 2000)
            .build()
    }

    @Bean
    fun deadlockRetryPolicy(): RetryPolicy {
        val exceptionClassifier = Classifier<Throwable, RetryPolicy> { throwable ->
            when {
                isDeadlockException(throwable) -> {
                    SimpleRetryPolicy(5)
                }
                else -> SimpleRetryPolicy(0)
            }
        }

        return ExceptionClassifierRetryPolicy().apply {
            setExceptionClassifier(exceptionClassifier)
        }
    }

    @Bean
    fun exponentialBackOffPolicy(): ExponentialBackOffPolicy {
        return ExponentialBackOffPolicy().apply {
            initialInterval = 100
            multiplier = 2.0
            maxInterval = 2000
        }
    }

    private fun isDeadlockException(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is SQLException) {
                val sqlState = current.sqlState
                val errorCode = current.errorCode
                if (sqlState == "40001" ||
                    errorCode == 1213 ||
                    current.message?.contains("deadlock", ignoreCase = true) == true) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }
}
