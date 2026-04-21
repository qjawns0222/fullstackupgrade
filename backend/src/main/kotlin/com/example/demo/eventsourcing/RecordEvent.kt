package com.example.demo.eventsourcing

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RecordEvent(
    val aggregateType: String,
    val eventType: String,
    val aggregateIdSpel: String = "",
    val actorSpel: String = ""
)
