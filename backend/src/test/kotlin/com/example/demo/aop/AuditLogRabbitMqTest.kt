package com.example.demo.aop

import com.example.demo.dto.AuditLogMessage
import com.example.demo.repository.AuditLogRepository
import com.example.demo.service.AuditLogProducer
import java.time.LocalDateTime
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@Disabled("Requires Docker environment")
class AuditLogRabbitMqTest {

    companion object {
        @Container val rabbitMQContainer = RabbitMQContainer("rabbitmq:3.12-management")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost)
            registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort)
            registry.add("spring.rabbitmq.username", rabbitMQContainer::getAdminUsername)
            registry.add("spring.rabbitmq.password", rabbitMQContainer::getAdminPassword)
        }
    }

    @Autowired private lateinit var auditLogProducer: AuditLogProducer

    @MockBean private lateinit var auditLogRepository: AuditLogRepository

    @Test
    fun `should send and consume audit log message`() {
        val message =
                AuditLogMessage(
                        userId = "test-user",
                        action = "TEST_RABBIT",
                        description = "Integration Test",
                        params = "{}",
                        status = "SUCCESS",
                        timestamp = LocalDateTime.now()
                )

        auditLogProducer.sendAuditLog(message)

        verify(auditLogRepository, timeout(5000).times(1)).save(any())
    }
}
