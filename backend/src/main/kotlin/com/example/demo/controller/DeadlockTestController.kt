package com.example.demo.controller

import com.example.demo.annotation.RetryOnDeadlock
import com.example.demo.entity.JobApplication
import com.example.demo.entity.JobApplicationStatus
import com.example.demo.entity.User
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.UserRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/deadlock-test")
class DeadlockTestController(
    private val jobApplicationRepository: JobApplicationRepository,
    private val userRepository: UserRepository,
    private val meterRegistry: MeterRegistry
) {

    private val executor: ExecutorService = Executors.newFixedThreadPool(10)

    @PostMapping("/simulate")
    fun simulateDeadlock(@RequestParam threads: Int = 5): Map<String, Any> {
        val testUser = getOrCreateTestUser()
        val testApplications = createTestApplications(testUser, threads)

        val futures = testApplications.map { app ->
            CompletableFuture.supplyAsync({
                try {
                    updateApplicationWithDeadlock(app.id!!)
                    "SUCCESS"
                } catch (e: Exception) {
                    "FAILED: ${e.message}"
                }
            }, executor)
        }

        val results = CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply { futures.map { it.join() } }
            .join()

        return mapOf(
            "threads" to threads,
            "results" to results,
            "retryCount" to meterRegistry.counter("database.deadlock.retry").count(),
            "successCount" to meterRegistry.counter("database.deadlock.success").count(),
            "failureCount" to meterRegistry.counter("database.deadlock.failure").count()
        )
    }

    @GetMapping("/metrics")
    fun getDeadlockMetrics(): Map<String, Double> {
        return mapOf(
            "retryCount" to meterRegistry.counter("database.deadlock.retry").count(),
            "successCount" to meterRegistry.counter("database.deadlock.success").count(),
            "failureCount" to meterRegistry.counter("database.deadlock.failure").count()
        )
    }

    @PostMapping("/reset")
    fun resetTestData(): String {
        jobApplicationRepository.deleteAll()
        return "Test data reset successfully"
    }

    @Transactional
    @RetryOnDeadlock
    fun updateApplicationWithDeadlock(id: Long) {
        val app = jobApplicationRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Application not found") }

        Thread.sleep((10..50).random().toLong())

        app.memo = "Updated at ${LocalDateTime.now()}"
        app.status = JobApplicationStatus.INTERVIEW
        app.updatedAt = LocalDateTime.now()

        jobApplicationRepository.save(app)

        Thread.sleep((10..50).random().toLong())
    }

    private fun getOrCreateTestUser(): User {
        val existingUser = userRepository.findByUsername("deadlock_test_user")
        if (existingUser.isPresent) {
            return existingUser.get()
        }

        val newUser = User(
            username = "deadlock_test_user",
            password = "password",
            email = "deadlock@test.com"
        )
        return userRepository.save(newUser)
    }

    private fun createTestApplications(user: User, count: Int): List<JobApplication> {
        jobApplicationRepository.deleteAll()

        val applications = (1..count).map {
            JobApplication(
                companyName = "Test Company $it",
                position = "Developer $it",
                status = JobApplicationStatus.APPLIED,
                appliedDate = LocalDate.now(),
                memo = "Initial memo",
                user = user
            )
        }

        return jobApplicationRepository.saveAll(applications)
    }
}
