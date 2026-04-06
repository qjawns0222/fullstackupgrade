package com.example.demo.audit

import com.example.demo.config.RabbitMqConfig
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class DlqMonitorService(
    private val dlqRepository: DlqRepository,
    private val auditLogRepository: AuditLogRepository,
    private val rabbitTemplate: RabbitTemplate
) {

    private val logger = LoggerFactory.getLogger(DlqMonitorService::class.java)

    /** DLQ 큐에서 메시지를 수신해 DB에 적재 */
    @RabbitListener(queues = [RabbitMqConfig.AUDIT_DLQ])
    @Transactional
    fun receiveDlqMessage(message: AuditLogMessage) {
        logger.warn("DLQ received failed audit message: action={}, userId={}", message.action, message.userId)
        val dlqMessage = DlqMessage(
            userId = message.userId,
            action = message.action,
            description = message.description,
            params = message.params,
            status = message.status,
            errorMessage = message.errorMessage,
            originalTimestamp = message.timestamp,
            dlqStatus = DlqStatus.PENDING
        )
        dlqRepository.save(dlqMessage)
        logger.info("DLQ message saved to DB: id={}", dlqMessage.id)
    }

    fun listMessages(status: DlqStatus?, pageable: Pageable): Page<DlqMessage> {
        return if (status != null) {
            dlqRepository.findByDlqStatus(status, pageable)
        } else {
            dlqRepository.findAll(pageable)
        }
    }

    fun getStats(): DlqStats = dlqRepository.getStats()

    /** 단건 재처리: ES에 직접 저장 시도 */
    @Transactional
    fun retry(id: Long): DlqMessage {
        val msg = dlqRepository.findById(id)
            .orElseThrow { NoSuchElementException("DLQ message not found: $id") }

        msg.dlqStatus = DlqStatus.RETRYING
        msg.retryCount++
        dlqRepository.save(msg)

        return try {
            val document = AuditLogDocument(
                userId = msg.userId,
                action = msg.action,
                description = msg.description,
                params = msg.params,
                status = msg.status,
                errorMessage = msg.errorMessage,
                timestamp = msg.originalTimestamp
            )
            auditLogRepository.save(document)

            msg.dlqStatus = DlqStatus.RESOLVED
            msg.resolvedAt = LocalDateTime.now()
            dlqRepository.save(msg).also {
                logger.info("DLQ message {} retried successfully", id)
            }
        } catch (e: Exception) {
            msg.dlqStatus = DlqStatus.PENDING
            msg.lastError = e.message
            dlqRepository.save(msg)
            logger.error("DLQ retry failed for id={}: {}", id, e.message)
            throw e
        }
    }

    /** 전체 PENDING 메시지 일괄 재처리 */
    @Transactional
    fun retryAll(): Int {
        val pending = dlqRepository.findByDlqStatus(DlqStatus.PENDING, Pageable.unpaged())
        var successCount = 0
        for (msg in pending) {
            runCatching { retry(msg.id) }.onSuccess { successCount++ }
        }
        logger.info("DLQ bulk retry completed: {}/{} succeeded", successCount, pending.totalElements)
        return successCount
    }

    /** 메시지 폐기 */
    @Transactional
    fun discard(id: Long): DlqMessage {
        val msg = dlqRepository.findById(id)
            .orElseThrow { NoSuchElementException("DLQ message not found: $id") }
        msg.dlqStatus = DlqStatus.DISCARDED
        msg.resolvedAt = LocalDateTime.now()
        return dlqRepository.save(msg).also {
            logger.info("DLQ message {} discarded", id)
        }
    }
}
