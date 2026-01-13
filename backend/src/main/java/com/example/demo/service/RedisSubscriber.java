package com.example.demo.service;

import com.example.demo.repository.SseEmitters;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SseEmitters sseEmitters;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            // Expecting JSON: { "username": "...", "content": "..." }
            JsonNode jsonNode = objectMapper.readTree(body);

            String username = jsonNode.get("username").asText();
            String content = jsonNode.get("content").asText();

            log.info("Redis message received for user: {}", username);
            sseEmitters.sendToUser(username, content);

        } catch (IOException e) {
            log.error("Error parsing Redis message", e);
        }
    }
}
