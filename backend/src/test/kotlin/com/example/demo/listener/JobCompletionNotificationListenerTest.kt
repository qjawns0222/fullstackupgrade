package com.example.demo.listener

import com.example.demo.controller.NotificationMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.messaging.simp.SimpMessagingTemplate

@ExtendWith(MockitoExtension::class)
class JobCompletionNotificationListenerTest {

    @Mock lateinit var template: SimpMessagingTemplate

    @InjectMocks lateinit var listener: JobCompletionNotificationListener

    @Test
    fun `should send notification when job completes`() {
        // Given
        val jobExecution = JobExecution(1L)
        jobExecution.status = BatchStatus.COMPLETED

        // When
        listener.afterJob(jobExecution)

        // Then
        verify(template)
                .convertAndSend(
                        "/topic/notifications",
                        NotificationMessage("Batch Job Completed: Tech Trend Analysis Finished!")
                )
    }

    @Test
    fun `should not send notification when job fails`() {
        // Given
        val jobExecution = JobExecution(1L)
        jobExecution.status = BatchStatus.FAILED

        // When
        listener.afterJob(jobExecution)

        // Then
        // verifying no interaction with template is too strict if logger is used, but template call
        // specifically:
        verify(template, org.mockito.Mockito.never())
                .convertAndSend(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(NotificationMessage::class.java)
                )
    }
}
