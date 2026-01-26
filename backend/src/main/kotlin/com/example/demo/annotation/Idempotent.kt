package com.example.demo.annotation

import java.util.concurrent.TimeUnit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
        val expireTime: Long = 60,
        val timeUnit: TimeUnit = TimeUnit.SECONDS,
        val keyHeader: String = "Idempotency-Key"
)
