package com.example.demo.listener;

import com.example.demo.entity.AnalysisRequest;
import com.example.demo.event.AiAnalysisEvent;
import com.example.demo.repository.AnalysisRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisEventListener {

    private final AnalysisRequestRepository repository;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final org.springframework.data.redis.listener.ChannelTopic topic;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Async
    @EventListener
    @Transactional
    public void handleAiAnalysis(AiAnalysisEvent event) {
        Long requestId = event.getAnalysisRequestId();
        log.info("Starting Async AI Analysis for Request ID: {}", requestId);

        AnalysisRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        request.startAnalysis();
        repository.saveAndFlush(request); // Ensure status update is committed

        try {
            // Simulate heavy AI processing
            Thread.sleep(5000);

            String mockResult = "AI Analysis Result for " + request.getOriginalFileName() + ": Success! (Mock Data)";
            request.complete(mockResult);
            log.info("AI Analysis Completed for Request ID: {}", requestId);

            // Publish to Redis
            java.util.Map<String, String> message = new java.util.HashMap<>();
            message.put("username", event.getUsername());
            message.put("content", "Analysis Completed for Request ID: " + requestId);

            String jsonMessage = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(topic.getTopic(), jsonMessage);
            log.info("Published notification to Redis for user: {}", event.getUsername());

        } catch (InterruptedException e) {
            log.error("Analysis interrupted", e);
            request.fail("Analysis interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Analysis failed", e);
            request.fail("Error: " + e.getMessage());
        }

        // Transactional will handle saving the final state (complete/fail)
    }
}
