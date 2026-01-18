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
class BatchScheduler(private val jobLauncher: JobLauncher, private val techTrendJob: Job) {

    @Scheduled(cron = "0 0/1 * * * *") // Runs every hour at minute 0
    fun runTechTrendJob() {
        val jobParameters =
                JobParametersBuilder()
                        .addString("timestamp", LocalDateTime.now().toString())
                        .toJobParameters()

        jobLauncher.run(techTrendJob, jobParameters)
    }
}
