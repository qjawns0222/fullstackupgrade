package com.example.demo.controller

import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
class BatchMonitoringController(
    private val jobExplorer: JobExplorer,
    private val jobLauncher: JobLauncher,
    private val techTrendJob: Job
) {

    @GetMapping("/jobs")
    fun getJobSummaries(): List<BatchJobSummary> {
        val jobNames = jobExplorer.jobNames
        return jobNames.flatMap { name ->
            val instances = jobExplorer.getJobInstances(name, 0, 10)
            instances.map { instance ->
                val lastExecution = jobExplorer.getJobExecutions(instance).lastOrNull()
                BatchJobSummary(
                    instanceId = instance.id,
                    jobName = name,
                    status = lastExecution?.status?.name ?: "UNKNOWN",
                    exitStatus = lastExecution?.exitStatus?.exitCode ?: "NONE",
                    startTime = lastExecution?.startTime,
                    endTime = lastExecution?.endTime
                )
            }
        }
    }

    @PostMapping("/run")
    fun runJob(@RequestParam jobName: String): String {
        if (jobName == "techTrendJob") {
            val params = JobParametersBuilder()
                .addString("timestamp", LocalDateTime.now().toString())
                .addString("source", "MANUAL_UI")
                .toJobParameters()
            jobLauncher.run(techTrendJob, params)
            return "Job started successfully"
        }
        return "Job not found"
    }

    data class BatchJobSummary(
        val instanceId: Long,
        val jobName: String,
        val status: String,
        val exitStatus: String,
        val startTime: LocalDateTime?,
        val endTime: LocalDateTime?
    )
}
