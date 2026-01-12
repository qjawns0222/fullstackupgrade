package com.example.demo.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseEmitters {

    // Thread-safe list to manage emitters
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter add(SseEmitter emitter) {
        this.emitters.add(emitter);
        log.info("New SseEmitter added. Current active emitters: {}", emitters.size());

        // Remove emitter on completion or timeout
        emitter.onCompletion(() -> {
            log.info("onCompletion callback");
            this.emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("onTimeout callback");
            emitter.complete();
        });

        // Send initial event to force connection establishment (flush buffer)
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (Exception e) {
            log.error("Error sending initial SSE event: {}", e.getMessage());
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void send(Object event) {
        log.info("Sending event to {} emitters", emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("analysis-complete")
                        .data(event));
            } catch (Exception e) {
                log.error("Error sending SSE event: {}", e.getMessage());
                emitter.completeWithError(e);
                emitters.remove(emitter); // Remove failed emitter
            }
        }
    }
}
