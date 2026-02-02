package com.example.demo.service

import com.example.demo.entity.TrendStats
import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class MailService(
        private val javaMailSender: JavaMailSender,
        private val templateEngine: SpringTemplateEngine,
        private val meterRegistry: io.micrometer.core.instrument.MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(MailService::class.java)

    @Async("mailExecutor")
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(
            name = "mailService",
            fallbackMethod = "fallbackSendWeeklyReport"
    )
    fun sendWeeklyReport(email: String, username: String, trends: List<TrendStats>) {
        logger.info("Sending weekly report to $email")

        try {
            val mimeMessage: MimeMessage = javaMailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

            helper.setTo(email)
            helper.setSubject("[TechTrend] 주간 기술 트렌드 리포트")

            val context = Context()
            context.setVariable("username", username)
            context.setVariable("trends", trends)

            val htmlContent = templateEngine.process("weekly-report", context)
            helper.setText(htmlContent, true)

            javaMailSender.send(mimeMessage)

            meterRegistry
                    .counter("mail.sent", "type", "weekly_report", "status", "success")
                    .increment()
            logger.info("Successfully sent weekly report to $email")
        } catch (e: MessagingException) {
            // Re-throw to trigger fallback/record failure if desired, or handle here.
            // If we catch here, Circuit Breaker might see 'Success'.
            // To make CB work, we should throw or use recordFailure predicate.
            // But existing code catches and logs.
            // I should modify it to THROW so CB detects failure.
            meterRegistry
                    .counter("mail.sent", "type", "weekly_report", "status", "failure")
                    .increment()
            logger.error("Failed to send email to $email", e)
            throw e
        } catch (e: Exception) {
            meterRegistry
                    .counter("mail.sent", "type", "weekly_report", "status", "failure")
                    .increment()
            logger.error("Unexpected error sending email to $email", e)
            throw e
        }
    }

    fun fallbackSendWeeklyReport(
            email: String,
            username: String,
            trends: List<TrendStats>,
            ex: Throwable
    ) {
        logger.warn(
                "Fallback triggered for $email. Circuit breaker status: OPEN or Error occurred. Reason: ${ex.message}"
        )
        meterRegistry
                .counter("mail.sent", "type", "weekly_report", "status", "fallback")
                .increment()
        // Logic to queue for later retry could go here
    }
}
