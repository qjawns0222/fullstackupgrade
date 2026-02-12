package com.example.demo.service

import com.example.demo.entity.TrendStats
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.mail.internet.MimeMessage
import java.util.UUID
import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.lambdas.JobLambda
import org.jobrunr.scheduling.JobScheduler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.spring6.SpringTemplateEngine

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    @Mock lateinit var javaMailSender: JavaMailSender
    @Mock lateinit var templateEngine: SpringTemplateEngine
    @Mock lateinit var meterRegistry: MeterRegistry
    @Mock lateinit var jobScheduler: JobScheduler
    @Mock lateinit var mimeMessage: MimeMessage
    @Mock lateinit var counter: Counter

    @InjectMocks lateinit var mailService: MailService

    @Test
    fun `sendWeeklyReportJob should enqueue job`() {
        // Arrange
        val email = "test@example.com"
        val username = "testuser"
        val trends = listOf<TrendStats>()

        val jobId = JobId(UUID.randomUUID())
        `when`(jobScheduler.enqueue(any(JobLambda::class.java))).thenReturn(jobId)

        // Act
        mailService.sendWeeklyReportJob(email, username, trends)

        // Assert
        verify(jobScheduler, times(1)).enqueue(any(JobLambda::class.java))
    }
}
