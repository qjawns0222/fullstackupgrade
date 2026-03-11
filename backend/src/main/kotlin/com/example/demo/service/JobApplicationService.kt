package com.example.demo.service

import com.example.demo.audit.AuditLog
import com.example.demo.dto.JobApplicationRequest
import com.example.demo.dto.JobApplicationResponse
import com.example.demo.entity.JobApplication
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.UserRepository
import com.example.demo.state.JobApplicationEvent
import com.example.demo.state.JobApplicationState
import java.time.LocalDateTime
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.statemachine.persist.StateMachinePersister
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class JobApplicationService(
        private val jobApplicationRepository: JobApplicationRepository,
        private val userRepository: UserRepository,
        private val stateMachineFactory:
                StateMachineFactory<JobApplicationState, JobApplicationEvent>,
        private val persister: StateMachinePersister<JobApplicationState, JobApplicationEvent, String>
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

    @Transactional
    @AuditLog(
            action = "CHANGE_STATUS",
            description = "User triggered a state transition for a job application"
    )
    fun changeStatus(id: Long, userId: Long, event: JobApplicationEvent): JobApplicationResponse {
        val application =
                jobApplicationRepository.findById(id).orElseThrow {
                    IllegalArgumentException("Application not found")
                }

        if (application.user.id != userId) {
            throw IllegalArgumentException("Unauthorized access")
        }

        // 1. Get State Machine from Factory
        val stateMachine = stateMachineFactory.getStateMachine(id.toString())

        // 2. Restore state from Persister (Redis)
        persister.restore(stateMachine, id.toString())

        // If newly created or not in Redis, reset to DB state as fallback
        if (stateMachine.state == null) {
            stateMachine.stopReactively().block()
            stateMachine.stateMachineAccessor.doWithAllRegions { accessor ->
                accessor.resetStateMachineReactively(
                                DefaultStateMachineContext(
                                        JobApplicationState.valueOf(application.status.name),
                                        null,
                                        null,
                                        null
                                )
                        )
                        .block()
            }
            stateMachine.startReactively().block()
        }

        // 3. Send Event
        stateMachine
                .sendEvent(Mono.just(MessageBuilder.withPayload(event).build()))
                .blockLast()

        // 4. Update Entity State based on Machine State
        val newState = stateMachine.state.id
        application.status = com.example.demo.entity.JobApplicationStatus.valueOf(newState.name)
        application.updatedAt = LocalDateTime.now()

        val savedApplication = jobApplicationRepository.save(application)

        // 5. Persist final state back to Redis
        persister.persist(stateMachine, id.toString())

        return toResponse(savedApplication)
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
