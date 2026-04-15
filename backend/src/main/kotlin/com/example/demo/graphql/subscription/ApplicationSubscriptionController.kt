package com.example.demo.graphql.subscription

import com.example.demo.notification.ApplicationStatusChangedEvent
import com.example.demo.notification.ApplicationSubscriptionService
import com.example.demo.repository.UserRepository
import org.springframework.graphql.data.method.annotation.SubscriptionMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux

@Controller
class ApplicationSubscriptionController(
    private val subscriptionService: ApplicationSubscriptionService,
    private val userRepository: UserRepository
) {

    @SubscriptionMapping
    fun applicationStatusChanged(
        @AuthenticationPrincipal userDetails: UserDetails
    ): Flux<ApplicationStatusChangedEvent> {
        val user = userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }
        return subscriptionService.statusChangesForUser(user.id!!)
    }
}
