package com.example.demo.audit

import com.example.demo.config.RabbitMqConfig
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class AuditLogConsumer(private val auditLogRepository: AuditLogRepository) {

    private val logger = LoggerFactory.getLogger(AuditLogConsumer::class.java)

    @RabbitListener(queues = [RabbitMqConfig.AUDIT_QUEUE])
    fun receiveAuditLog(message: AuditLogMessage) {
        val document = AuditLogDocument(
            userId = message.userId,
            action = message.action,
            description = message.description,
            params = message.params,
            status = message.status,
            errorMessage = message.errorMessage,
            timestamp = message.timestamp
        )
        auditLogRepository.save(document)
        logger.info("Consumed and saved audit log: action={}, userId={}", message.action, message.userId)
        // 예외 발생 시 Spring AMQP가 자동으로 x-death 헤더를 추가하고 DLX/DLQ로 라우팅
    }
}
