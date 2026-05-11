package com.example.demo.audit

import com.example.demo.config.RabbitMqConfig
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class AuditLogConsumer(private val pipeline: ReactiveAuditPipeline) {

    private val logger = LoggerFactory.getLogger(AuditLogConsumer::class.java)

    @RabbitListener(queues = [RabbitMqConfig.AUDIT_QUEUE])
    fun receiveAuditLog(message: AuditLogMessage) {
        pipeline.emit(message)
        logger.debug("Emitted audit message to reactive pipeline: action={}, userId={}", message.action, message.userId)
        // 예외 발생 시 Spring AMQP가 자동으로 x-death 헤더를 추가하고 DLX/DLQ로 라우팅
    }
}
