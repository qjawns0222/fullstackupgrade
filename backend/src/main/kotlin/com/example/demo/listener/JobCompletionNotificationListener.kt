package com.example.demo.listener

import com.example.demo.repository.TrendStatsRepository
import com.example.demo.repository.UserRepository
import com.example.demo.service.MailService
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.stereotype.Component

@Component
class JobCompletionNotificationListener(
        private val mailService: MailService,
        private val userRepository: UserRepository,
        private val trendStatsRepository: TrendStatsRepository
) : JobExecutionListener {

    private val logger = LoggerFactory.getLogger(JobCompletionNotificationListener::class.java)

    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.status == BatchStatus.COMPLETED) {
            logger.info("!!! JOB FINISHED! Time to send emails")
            // Email sending logic moved to Batch Step (sendNotificationStep) for performance and
            // safety
        }
    }
}
