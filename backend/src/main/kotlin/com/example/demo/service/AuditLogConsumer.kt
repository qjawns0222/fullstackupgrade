package com.example.demo.service

import com.example.demo.config.RabbitMqConfig
import com.example.demo.document.AuditLogDocument
import com.example.demo.dto.AuditLogMessage
import com.example.demo.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class AuditLogConsumer(private val auditLogRepository: AuditLogRepository) {

    private val logger = LoggerFactory.getLogger(AuditLogConsumer::class.java)

    @RabbitListener(queues = [RabbitMqConfig.AUDIT_QUEUE])
    fun receiveAuditLog(message: AuditLogMessage) {
        try {
            val document =
                    AuditLogDocument(
                            userId = message.userId,
                            action = message.action,
                            description = message.description,
                            params = message.params,
                            status = message.status,
                            errorMessage = message.errorMessage,
                            timestamp = message.timestamp
                    )
            auditLogRepository.save(document)
            logger.info("Consumed and saved audit log: $document")
        } catch (e: Exception) {
            logger.error("Failed to process audit log message: $message", e)
        }
    }
}
