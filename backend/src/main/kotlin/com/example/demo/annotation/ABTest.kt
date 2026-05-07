package com.example.demo.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ABTest(
    val toggleName: String,
    val trackEvent: Boolean = true
)
