package com.example.demo.graphql

import com.example.demo.entity.JobApplicationStatus
import com.example.demo.notification.ApplicationStatusChangedEvent
import com.example.demo.notification.ApplicationSubscriptionService
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeoutException

class ApplicationSubscriptionServiceTest {

    private val service = ApplicationSubscriptionService()

    @Test
    fun `statusChanges emits published events`() {
        val event = ApplicationStatusChangedEvent(
            applicationId = 1L,
            companyName = "TestCorp",
            position = "Backend Dev",
            newStatus = JobApplicationStatus.INTERVIEW,
            userId = 42L
        )

        val flux = service.statusChanges()

        StepVerifier.create(flux.take(1))
            .then { service.publish(event) }
            .expectNextMatches { e -> e.applicationId == 1L && e.companyName == "TestCorp" }
            .verifyComplete()
    }

    @Test
    fun `statusChangesForUser filters events by userId`() {
        val eventUser42 = ApplicationStatusChangedEvent(
            applicationId = 1L,
            companyName = "Corp A",
            position = "Dev",
            newStatus = JobApplicationStatus.INTERVIEW,
            userId = 42L
        )
        val eventUser99 = ApplicationStatusChangedEvent(
            applicationId = 2L,
            companyName = "Corp B",
            position = "Dev",
            newStatus = JobApplicationStatus.REJECTED,
            userId = 99L
        )

        val flux = service.statusChangesForUser(42L)

        StepVerifier.create(flux.take(1))
            .then {
                service.publish(eventUser99)
                service.publish(eventUser42)
            }
            .expectNextMatches { e -> e.userId == 42L && e.companyName == "Corp A" }
            .verifyComplete()
    }

    @Test
    fun `statusChangesForUser does not emit events for other users`() {
        val eventUser99 = ApplicationStatusChangedEvent(
            applicationId = 2L,
            companyName = "Corp B",
            position = "Dev",
            newStatus = JobApplicationStatus.REJECTED,
            userId = 99L
        )

        val flux = service.statusChangesForUser(42L)

        StepVerifier.create(flux.take(1).timeout(Duration.ofMillis(300)))
            .then { service.publish(eventUser99) }
            .expectError(TimeoutException::class.java)
            .verify()
    }
}
