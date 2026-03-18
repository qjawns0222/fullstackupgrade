package com.example.demo.graphql.mutation

import com.example.demo.dto.JobApplicationRequest
import com.example.demo.entity.JobApplication
import com.example.demo.entity.JobApplicationStatus
import com.example.demo.repository.UserRepository
import com.example.demo.service.JobApplicationService
import com.example.demo.state.JobApplicationEvent
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import java.time.LocalDate

data class CreateApplicationInput(
    val companyName: String,
    val position: String,
    val status: String,
    val appliedDate: LocalDate,
    val memo: String?
)

data class UpdateApplicationInput(
    val companyName: String,
    val position: String,
    val status: String,
    val appliedDate: LocalDate,
    val memo: String?
)

@Controller
class JobApplicationMutationController(
    private val jobApplicationService: JobApplicationService,
    private val userRepository: UserRepository
) {

    private fun getUserId(userDetails: UserDetails): Long =
        userRepository.findByUsername(userDetails.username)
            .orElseThrow { IllegalArgumentException("User not found") }.id!!

    @MutationMapping
    fun createApplication(
        @Argument input: CreateApplicationInput,
        @AuthenticationPrincipal userDetails: UserDetails
    ): JobApplication {
        val userId = getUserId(userDetails)
        val request = JobApplicationRequest(
            companyName = input.companyName,
            position = input.position,
            status = JobApplicationStatus.valueOf(input.status),
            appliedDate = input.appliedDate,
            memo = input.memo
        )
        val response = jobApplicationService.createApplication(userId, request)
        return jobApplicationService.getApplicationEntity(response.id, userId)
    }

    @MutationMapping
    fun updateApplication(
        @Argument id: Long,
        @Argument input: UpdateApplicationInput,
        @AuthenticationPrincipal userDetails: UserDetails
    ): JobApplication {
        val userId = getUserId(userDetails)
        val request = JobApplicationRequest(
            companyName = input.companyName,
            position = input.position,
            status = JobApplicationStatus.valueOf(input.status),
            appliedDate = input.appliedDate,
            memo = input.memo
        )
        val response = jobApplicationService.updateApplication(id, userId, request)
        return jobApplicationService.getApplicationEntity(response.id, userId)
    }

    @MutationMapping
    fun changeApplicationStatus(
        @Argument id: Long,
        @Argument event: JobApplicationEvent,
        @AuthenticationPrincipal userDetails: UserDetails
    ): JobApplication {
        val userId = getUserId(userDetails)
        val response = jobApplicationService.changeStatus(id, userId, event)
        return jobApplicationService.getApplicationEntity(response.id, userId)
    }

    @MutationMapping
    fun deleteApplication(
        @Argument id: Long,
        @AuthenticationPrincipal userDetails: UserDetails
    ): Boolean {
        val userId = getUserId(userDetails)
        jobApplicationService.deleteApplication(id, userId)
        return true
    }
}
