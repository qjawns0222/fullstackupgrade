package com.example.demo.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class JobCompletionNotificationListener(private val template: SimpMessagingTemplate) :
        JobExecutionListener {

    private val logger = LoggerFactory.getLogger(JobCompletionNotificationListener::class.java)

    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.status == BatchStatus.COMPLETED) {
            logger.info("!!! JOB FINISHED! Broadcasting notification via WebSocket")
            template.convertAndSend(
                    "/topic/notifications",
                    com.example.demo.dto.NotificationMessage(
                            "Batch Job Completed: Tech Trend Analysis Finished!"
                    )
            )
        }
    }
}
