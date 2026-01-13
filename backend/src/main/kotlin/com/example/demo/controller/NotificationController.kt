package com.example.demo.controller

import com.example.demo.repository.SseEmitters
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.security.Principal

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val sseEmitters: SseEmitters
) {

    private val log = LoggerFactory.getLogger(NotificationController::class.java)

    @GetMapping(value = ["/subscribe"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribe(principal: Principal?): SseEmitter {
        val username = principal?.name ?: "anonymous"

        // Timeout: 60 seconds (can be adjusted)
        val emitter = SseEmitter(60 * 1000L)
        return sseEmitters.add(username, emitter)
    }
}
