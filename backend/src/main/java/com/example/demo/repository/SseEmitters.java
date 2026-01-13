package com.example.demo.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitters {

    // Manage emitters by Username/UserId
    // Key: Username, Value: List of emitters (for multiple tabs/devices)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter add(String username, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.computeIfAbsent(username, k -> new CopyOnWriteArrayList<>());
        userEmitters.add(emitter);
        log.info("New SseEmitter added for user: {}. Current active emitters for user: {}", username,
                userEmitters.size());

        // Remove emitter on completion or timeout
        emitter.onCompletion(() -> {
            log.info("onCompletion callback for user: {}", username);
            removeEmitter(username, emitter);
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout callback for user: {}", username);
            emitter.complete();
            removeEmitter(username, emitter);
        });
        emitter.onError((e) -> {
            log.error("onError callback for user: {}", username, e);
            emitter.complete();
            removeEmitter(username, emitter);
        });

        // Send initial event
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (Exception e) {
            log.error("Error sending initial SSE event: {}", e.getMessage());
            emitter.completeWithError(e);
            removeEmitter(username, emitter);
        }

        return emitter;
    }

    private void removeEmitter(String username, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(username);
            }
        }
    }

    // Broadcast to ALL users (optional, kept for compatibility if needed)
    public void send(Object event) {
        // ... (Optional implementation if needed, or remove)
    }

    // Send to Specific User
    public void sendToUser(String username, Object event) {
        List<SseEmitter> userEmitters = emitters.get(username);
        if (userEmitters == null || userEmitters.isEmpty()) {
            log.info("No active emitters for user: {}", username);
            return;
        }

        log.info("Sending event to user: {} ({} emitters)", username, userEmitters.size());
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("analysis-complete")
                        .data(event));
            } catch (Exception e) {
                log.error("Error sending SSE event to user {}: {}", username, e.getMessage());
                emitter.completeWithError(e);
                removeEmitter(username, emitter);
            }
        }
    }
}
