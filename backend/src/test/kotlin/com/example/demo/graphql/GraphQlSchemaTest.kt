package com.example.demo.graphql

import com.example.demo.entity.JobApplication
import com.example.demo.entity.JobApplicationStatus
import com.example.demo.entity.Resume
import com.example.demo.entity.TrendStats
import com.example.demo.entity.User
import com.example.demo.graphql.dataloader.UserDataLoader
import com.example.demo.graphql.mutation.JobApplicationMutationController
import com.example.demo.graphql.query.DashboardQueryController
import com.example.demo.graphql.query.JobApplicationQueryController
import com.example.demo.graphql.query.ResumeQueryController
import com.example.demo.graphql.query.TrendStatsQueryController
import com.example.demo.graphql.query.UserQueryController
import com.example.demo.repository.JobApplicationRepository
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.TrendStatsRepository
import com.example.demo.repository.UserRepository
import com.example.demo.service.DashboardData
import com.example.demo.service.DashboardService
import com.example.demo.service.JobApplicationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.security.test.context.support.WithMockUser
import java.time.LocalDate
import java.time.LocalDateTime

@GraphQlTest(
    controllers = [
        TrendStatsQueryController::class,
        JobApplicationQueryController::class,
        ResumeQueryController::class,
        UserQueryController::class,
        DashboardQueryController::class,
        JobApplicationMutationController::class
    ]
)
@Import(com.example.demo.config.GraphQlConfig::class)
class GraphQlSchemaTest {

    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @MockBean
    private lateinit var trendStatsRepository: TrendStatsRepository

    @MockBean
    private lateinit var jobApplicationRepository: JobApplicationRepository

    @MockBean
    private lateinit var resumeRepository: ResumeRepository

    @MockBean
    private lateinit var userRepository: UserRepository

    @MockBean
    private lateinit var dashboardService: DashboardService

    @MockBean
    private lateinit var jobApplicationService: JobApplicationService

    @MockBean
    private lateinit var userDataLoader: UserDataLoader

    @Test
    fun `trends query returns list`() {
        val stats = TrendStats(techStack = "Kotlin", count = 42L)
        stats.let { it.javaClass.getDeclaredField("id").also { f -> f.isAccessible = true }.set(it, 1L) }
        `when`(trendStatsRepository.findAll()).thenReturn(listOf(stats))

        graphQlTester.document("""
            { trends { id techStack count } }
        """.trimIndent())
            .execute()
            .path("trends[0].techStack")
            .entity(String::class.java)
            .isEqualTo("Kotlin")
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `myApplications query returns list for authenticated user`() {
        val user = User(id = 1L, username = "testuser", role = "ROLE_USER")
        val application = JobApplication(
            companyName = "TestCo",
            position = "Backend Dev",
            status = JobApplicationStatus.APPLIED,
            appliedDate = LocalDate.now(),
            user = user
        )
        application.javaClass.getDeclaredField("id").also { it.isAccessible = true }.set(application, 1L)

        `when`(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user))
        `when`(jobApplicationRepository.findAllByUserId(1L)).thenReturn(listOf(application))

        graphQlTester.document("""
            { myApplications { id companyName position status } }
        """.trimIndent())
            .execute()
            .path("myApplications[0].companyName")
            .entity(String::class.java)
            .isEqualTo("TestCo")
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `dashboard query returns cached data`() {
        `when`(dashboardService.getDashboardData("testuser")).thenReturn(
            DashboardData(userId = "testuser", data = "SomeData", timestamp = LocalDateTime.now().toString())
        )

        graphQlTester.document("""
            { dashboard { userId data } }
        """.trimIndent())
            .execute()
            .path("dashboard.userId")
            .entity(String::class.java)
            .isEqualTo("testuser")
    }
}
