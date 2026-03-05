package com.example.demo.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
        val key: String = "",
        val capacity: Long = 10,
        val tokens: Long = 10,
        val seconds: Long = 60
)
