package com.example.demo.scheduler

import java.time.LocalDateTime
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@Configuration
@EnableScheduling
class BatchScheduler(
        private val jobLauncher: JobLauncher,
        private val techTrendJob: Job,
        private val meterRegistry: io.micrometer.core.instrument.MeterRegistry
) {

    // Runs every Monday at 09:00 AM
    @Scheduled(cron = "0 0 * * * ?")
    fun runTechTrendJob() {
        val jobParameters =
                JobParametersBuilder()
                        .addString("timestamp", LocalDateTime.now().toString())
                        .toJobParameters()

        try {
            val jobExecution = jobLauncher.run(techTrendJob, jobParameters)
            val status = jobExecution.status.name.lowercase()
            meterRegistry
                    .counter("batch.job.result", "job", "techTrendJob", "result", status)
                    .increment()
        } catch (e: Exception) {
            meterRegistry
                    .counter("batch.job.result", "job", "techTrendJob", "result", "failed")
                    .increment()
            throw e
        }
    }
}
