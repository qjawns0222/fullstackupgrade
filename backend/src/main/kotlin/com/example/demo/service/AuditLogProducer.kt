package com.example.demo.service

import com.example.demo.config.RabbitMqConfig
import com.example.demo.dto.AuditLogMessage
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service

@Service
class AuditLogProducer(private val rabbitTemplate: RabbitTemplate) {

    private val logger = LoggerFactory.getLogger(AuditLogProducer::class.java)

    fun sendAuditLog(message: AuditLogMessage) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.AUDIT_EXCHANGE,
                    RabbitMqConfig.AUDIT_ROUTING_KEY,
                    message
            )
            logger.info("Sent audit log to RabbitMQ: $message")
        } catch (e: Exception) {
            logger.error("Failed to send audit log to RabbitMQ", e)
            // Fallback mechanism could be implemented here (e.g., save to local file or DB
            // directly)
        }
    }
}
