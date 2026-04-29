package com.example.demo.notification

import com.example.demo.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications/preferences")
class NotificationHubController(
    private val preferenceService: NotificationPreferenceService,
    private val notificationRouter: NotificationRouter,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getPreferences(@AuthenticationPrincipal userDetails: UserDetails): List<NotificationPreferenceResponse> {
        val user = resolveUser(userDetails)
        return preferenceService.getPreferences(user.id!!)
    }

    @PutMapping
    fun upsertPreference(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestBody request: NotificationPreferenceRequest
    ): NotificationPreferenceResponse {
        val user = resolveUser(userDetails)
        return preferenceService.upsertPreference(user, request)
    }

    @DeleteMapping("/{channel}")
    fun deletePreference(
        @AuthenticationPrincipal userDetails: UserDetails,
        @PathVariable channel: NotificationChannel
    ): ResponseEntity<Void> {
        val user = resolveUser(userDetails)
        preferenceService.deletePreference(user.id!!, channel)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/test")
    fun testRoute(
        @AuthenticationPrincipal userDetails: UserDetails,
        @RequestBody event: NotificationEvent
    ): ResponseEntity<Void> {
        val user = resolveUser(userDetails)
        notificationRouter.route(user.id!!, event)
        return ResponseEntity.accepted().build()
    }

    private fun resolveUser(userDetails: UserDetails) =
        userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found: ${userDetails.username}") }
}
