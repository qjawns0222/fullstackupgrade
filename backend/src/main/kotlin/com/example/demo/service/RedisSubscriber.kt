package com.example.demo.service

import com.example.demo.repository.SseEmitters
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class RedisSubscriber(
    private val objectMapper: ObjectMapper,
    private val sseEmitters: SseEmitters
) : MessageListener {

    private val log = LoggerFactory.getLogger(RedisSubscriber::class.java)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val body = String(message.body)
            // Expecting JSON: { "username": "...", "content": "..." }
            val jsonNode = objectMapper.readTree(body)

            val username = jsonNode.get("username").asText()
            val content = jsonNode.get("content").asText()

            log.info("Redis message received for user: {}", username)
            sseEmitters.sendToUser(username, content)

        } catch (e: IOException) {
            log.error("Error parsing Redis message", e)
        }
    }
}
