package com.example.demo.service

import com.example.demo.dto.JobApplicationRequest
import com.example.demo.entity.JobApplication
import com.example.demo.entity.JobApplicationStatus
import com.example.demo.entity.User
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.UserRepository
import java.time.LocalDate
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class JobApplicationServiceTest {

    @Mock private lateinit var jobApplicationRepository: JobApplicationRepository

    @Mock private lateinit var userRepository: UserRepository

    @InjectMocks private lateinit var jobApplicationService: JobApplicationService

    @Test
    fun `createApplication should save and return application`() {
        // Given
        val userId = 1L

        // Mock User
        val user =
                User(
                        id = userId,
                        username = "testuser",
                        password = "password",
                        role = "USER",
                        email = "test@example.com"
                )

        val request =
                JobApplicationRequest(
                        companyName = "Test Company",
                        position = "Developer",
                        status = JobApplicationStatus.APPLIED,
                        appliedDate = LocalDate.now(),
                        memo = "Test Memo"
                )

        // Mock returns
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))

        val savedApplication =
                JobApplication(
                                companyName = request.companyName,
                                position = request.position,
                                status = request.status,
                                appliedDate = request.appliedDate,
                                memo = request.memo,
                                user = user
                        )
                        .apply { id = 100L }

        `when`(jobApplicationRepository.save(any(JobApplication::class.java)))
                .thenReturn(savedApplication)

        // When
        val result = jobApplicationService.createApplication(userId, request)

        // Then
        assertNotNull(result)
        assertEquals(request.companyName, result.companyName)
        assertEquals(userId, result.userId)
        verify(jobApplicationRepository).save(any(JobApplication::class.java))
    }
}
