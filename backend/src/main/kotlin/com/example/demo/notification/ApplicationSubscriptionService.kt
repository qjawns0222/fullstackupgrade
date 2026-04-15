package com.example.demo.notification

import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Service
class ApplicationSubscriptionService {

    private val sink: Sinks.Many<ApplicationStatusChangedEvent> =
        Sinks.many().multicast().onBackpressureBuffer()

    fun publish(event: ApplicationStatusChangedEvent) {
        sink.tryEmitNext(event)
    }

    fun statusChanges(): Flux<ApplicationStatusChangedEvent> =
        sink.asFlux()

    fun statusChangesForUser(userId: Long): Flux<ApplicationStatusChangedEvent> =
        sink.asFlux().filter { it.userId == userId }
}
