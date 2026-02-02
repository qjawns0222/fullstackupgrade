package com.example.demo.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@ExtendWith(MockitoExtension::class)
class MailServiceCircuitBreakerTest {

    @Mock private lateinit var javaMailSender: JavaMailSender

    @Mock private lateinit var templateEngine: SpringTemplateEngine

    @Mock private lateinit var meterRegistry: MeterRegistry

    @Mock private lateinit var counter: Counter

    @InjectMocks private lateinit var mailService: MailService

    @Test
    fun `sendWeeklyReport should send mail successfully`() {
        // Setup
        val mimeMessage = Mockito.mock(MimeMessage::class.java)
        Mockito.`when`(javaMailSender.createMimeMessage()).thenReturn(mimeMessage)
        Mockito.`when`(
                        templateEngine.process(
                                Mockito.anyString(),
                                Mockito.any(Context::class.java)
                        )
                )
                .thenReturn("<html></html>")
        Mockito.`when`(
                        meterRegistry.counter(
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString()
                        )
                )
                .thenReturn(counter)

        // Execute
        mailService.sendWeeklyReport("test@example.com", "User", emptyList())

        // Verify
        Mockito.verify(javaMailSender).send(mimeMessage)
    }

    @Test
    fun `fallbackSendWeeklyReport should log and not throw`() {
        // Setup
        Mockito.`when`(
                        meterRegistry.counter(
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString()
                        )
                )
                .thenReturn(counter)

        // Execute
        mailService.fallbackSendWeeklyReport(
                "test@example.com",
                "User",
                emptyList(),
                RuntimeException("Test")
        )

        // Verify
        Mockito.verify(meterRegistry)
                .counter("mail.sent", "type", "weekly_report", "status", "fallback")
    }
}
