package com.example.demo.service

import com.example.demo.entity.TrendStats
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class MailService(
        private val javaMailSender: JavaMailSender,
        private val templateEngine: SpringTemplateEngine,
        private val meterRegistry: io.micrometer.core.instrument.MeterRegistry,
        private val jobScheduler: org.jobrunr.scheduling.JobScheduler
) {

        private val logger = LoggerFactory.getLogger(MailService::class.java)

        fun sendWeeklyReportJob(email: String, username: String, trends: List<TrendStats>) {
                jobScheduler.enqueue { sendWeeklyReport(email, username, trends) }
        }

        @org.jobrunr.jobs.annotations.Job(name = "Send Weekly Report to %0", retries = 5)
        fun sendWeeklyReport(email: String, username: String, trends: List<TrendStats>) {
                logger.info("Executing periodic job: Sending weekly report to $email")

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
                        logger.info("Successfully sent weekly report to $email via JobRunr")
                } catch (e: Exception) {
                        meterRegistry
                                .counter("mail.sent", "type", "weekly_report", "status", "failure")
                                .increment()
                        logger.error("Failed to send email to $email", e)
                        throw e // JobRunr will retry
                }
        }
}
