package com.example.demo.service

import com.example.demo.annotation.AuditLog
import com.example.demo.dto.JobApplicationRequest
import com.example.demo.dto.JobApplicationResponse
import com.example.demo.entity.JobApplication
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.UserRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobApplicationService(
        private val jobApplicationRepository: JobApplicationRepository,
        private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun getAllApplications(userId: Long): List<JobApplicationResponse> {
        return jobApplicationRepository.findAllByUserId(userId).map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getApplication(id: Long, userId: Long): JobApplicationResponse {
        val application =
                jobApplicationRepository.findById(id).orElseThrow {
                    IllegalArgumentException("Application not found")
                }
        if (application.user.id != userId) {
            throw IllegalArgumentException("Unauthorized access")
        }
        return toResponse(application)
    }

    @Transactional
    @AuditLog(action = "CREATE_APPLICATION", description = "User created a new job application")
    fun createApplication(userId: Long, request: JobApplicationRequest): JobApplicationResponse {
        val user =
                userRepository.findById(userId).orElseThrow {
                    IllegalArgumentException("User not found")
                }

        val application =
                JobApplication(
                        companyName = request.companyName,
                        position = request.position,
                        status = request.status,
                        appliedDate = request.appliedDate,
                        memo = request.memo,
                        user = user
                )

        return toResponse(jobApplicationRepository.save(application))
    }

    @Transactional
    @AuditLog(action = "UPDATE_APPLICATION", description = "User updated a job application")
    fun updateApplication(
            id: Long,
            userId: Long,
            request: JobApplicationRequest
    ): JobApplicationResponse {
        val application =
                jobApplicationRepository.findById(id).orElseThrow {
                    IllegalArgumentException("Application not found")
                }

        if (application.user.id != userId) {
            throw IllegalArgumentException("Unauthorized access")
        }

        application.companyName = request.companyName
        application.position = request.position
        application.status = request.status
        application.appliedDate = request.appliedDate
        application.memo = request.memo
        application.updatedAt = LocalDateTime.now()

        return toResponse(application)
    }

    @Transactional
    @AuditLog(action = "DELETE_APPLICATION", description = "User deleted a job application")
    fun deleteApplication(id: Long, userId: Long) {
        val application =
                jobApplicationRepository.findById(id).orElseThrow {
                    IllegalArgumentException("Application not found")
                }

        if (application.user.id != userId) {
            throw IllegalArgumentException("Unauthorized access")
        }

        jobApplicationRepository.delete(application)
    }

    private fun toResponse(application: JobApplication): JobApplicationResponse {
        return JobApplicationResponse(
                id = application.id!!,
                companyName = application.companyName,
                position = application.position,
                status = application.status,
                appliedDate = application.appliedDate,
                memo = application.memo,
                userId = application.user.id!!
        )
    }
}
