package com.example.demo.repository

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEmitters {

    private val log = LoggerFactory.getLogger(SseEmitters::class.java)

    // Manage emitters by Username/UserId
    // Key: Username, Value: List of emitters (for multiple tabs/devices)
    private val emitters: MutableMap<String, MutableList<SseEmitter>> = ConcurrentHashMap()

    fun add(username: String, emitter: SseEmitter): SseEmitter {
        val userEmitters = emitters.computeIfAbsent(username) { CopyOnWriteArrayList() }
        userEmitters.add(emitter)
        log.info("New SseEmitter added for user: {}. Current active emitters for user: {}", username, userEmitters.size)

        // Remove emitter on completion or timeout
        emitter.onCompletion {
            log.info("onCompletion callback for user: {}", username)
            removeEmitter(username, emitter)
        }
        emitter.onTimeout {
            log.info("onTimeout callback for user: {}", username)
            emitter.complete()
            removeEmitter(username, emitter)
        }
        emitter.onError { e ->
            log.error("onError callback for user: {}", username, e)
            emitter.complete()
            removeEmitter(username, emitter)
        }

        // Send initial event
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connect")
                    .data("connected!")
            )
        } catch (e: Exception) {
            log.error("Error sending initial SSE event: {}", e.message)
            emitter.completeWithError(e)
            removeEmitter(username, emitter)
        }

        return emitter
    }

    private fun removeEmitter(username: String, emitter: SseEmitter) {
        val userEmitters = emitters[username]
        if (userEmitters != null) {
            userEmitters.remove(emitter)
            if (userEmitters.isEmpty()) {
                emitters.remove(username)
            }
        }
    }

    // Broadcast to ALL users (optional, kept for compatibility if needed)
    fun send(event: Any) {
        // ... (Optional implementation if needed, or remove)
    }

    // Send to Specific User
    fun sendToUser(username: String, event: Any) {
        val userEmitters = emitters[username]
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.info("No active emitters for user: {}", username)
            return
        }

        log.info("Sending event to user: {} ({} emitters)", username, userEmitters.size)
        for (emitter in userEmitters) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("analysis-complete")
                        .data(event)
                )
            } catch (e: Exception) {
                log.error("Error sending SSE event to user {}: {}", username, e.message)
                emitter.completeWithError(e)
                removeEmitter(username, emitter)
            }
        }
    }
}
