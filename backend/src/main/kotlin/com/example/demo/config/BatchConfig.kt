package com.example.demo.config

import com.example.demo.entity.Resume
import com.example.demo.entity.TrendStats
import com.example.demo.listener.JobCompletionNotificationListener
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.TrendStatsRepository
import com.example.demo.repository.UserRepository
import com.example.demo.service.MailService
import java.util.concurrent.ConcurrentHashMap
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.data.RepositoryItemReader
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class BatchConfig(
        private val jobRepository: JobRepository,
        private val transactionManager: PlatformTransactionManager,
        private val resumeRepository: ResumeRepository,
        private val trendStatsRepository: TrendStatsRepository,
        private val jobCompletionNotificationListener: JobCompletionNotificationListener,
        private val userRepository: UserRepository,
        private val mailService: MailService
) {

    // Shared state for the job execution to accumulate counts
    private val techStackCounts = ConcurrentHashMap<String, Long>()

    // Keywords to search for
    private val keywords =
            listOf(
                    "Java",
                    "Kotlin",
                    "Python",
                    "JavaScript",
                    "TypeScript",
                    "React",
                    "Vue",
                    "Spring",
                    "NestJS",
                    "Node.js",
                    "Go",
                    "Rust"
            )

    @Bean
    fun techTrendJob(): Job {
        return JobBuilder("techTrendJob", jobRepository)
                .start(trendAnalysisStep())
                .next(saveTrendStatsStep())
                .next(sendNotificationStep())
                .listener(jobCompletionNotificationListener)
                .build()
    }

    @Bean
    fun trendAnalysisStep(): Step {
        return StepBuilder("trendAnalysisStep", jobRepository)
                .chunk<Resume, List<String>>(100, transactionManager)
                .reader(resumeReader())
                .processor(resumeProcessor())
                .writer(resumeWriter())
                .listener(
                        object : org.springframework.batch.core.StepExecutionListener {
                            override fun beforeStep(
                                    stepExecution: org.springframework.batch.core.StepExecution
                            ) {
                                techStackCounts.clear()
                            }
                        }
                )
                .build()
    }

    @Bean
    fun resumeReader(): RepositoryItemReader<Resume> {
        return RepositoryItemReaderBuilder<Resume>()
                .name("resumeReader")
                .repository(resumeRepository)
                .methodName("findAll")
                .pageSize(100)
                .sorts(mapOf("id" to Sort.Direction.ASC))
                .build()
    }

    @Bean
    fun resumeProcessor(): ItemProcessor<Resume, List<String>> {
        return ItemProcessor { resume ->
            val content = resume.content ?: ""
            keywords.filter { keyword -> content.contains(keyword, ignoreCase = true) }
        }
    }

    @Bean
    fun resumeWriter(): ItemWriter<List<String>> {
        return ItemWriter { chunk ->
            println("=== Processing Chunk of size: ${chunk.size()} ===")
            chunk.items.forEach { foundKeywords ->
                foundKeywords.forEach { keyword -> techStackCounts.merge(keyword, 1L, Long::plus) }
            }
        }
    }

    @Bean
    fun saveTrendStatsStep(): Step {
        return StepBuilder("saveTrendStatsStep", jobRepository)
                .tasklet(saveStatsTasklet(), transactionManager)
                .build()
    }

    @Bean
    fun saveStatsTasklet(): Tasklet {
        return Tasklet { _, _ ->
            if (techStackCounts.isNotEmpty()) {
                val stats =
                        techStackCounts.map { (tech, count) ->
                            TrendStats(techStack = tech, count = count)
                        }
                trendStatsRepository.saveAll(stats)
                techStackCounts.clear()
            }
            RepeatStatus.FINISHED
        }
    }

    // === Email Notification Step ===

    @Bean
    fun sendNotificationStep(): Step {
        return StepBuilder("sendNotificationStep", jobRepository)
                .chunk<com.example.demo.entity.User, com.example.demo.entity.User>(
                        10,
                        transactionManager
                ) // Chunk size 10 to throttle emails
                .reader(userReader())
                .writer(emailWriter())
                .build()
    }

    @Bean
    fun userReader(): RepositoryItemReader<com.example.demo.entity.User> {
        return RepositoryItemReaderBuilder<com.example.demo.entity.User>()
                .name("userReader")
                .repository(userRepository)
                .methodName("findAll")
                .pageSize(10)
                .sorts(mapOf("id" to Sort.Direction.ASC))
                .build()
    }

    @Bean
    fun emailWriter(): ItemWriter<com.example.demo.entity.User> {
        return ItemWriter { users ->
            // Fetch latest trends once per chunk to send in email
            val latestTrends = trendStatsRepository.findTop12ByOrderByRecordedAtDesc()
            users.forEach { user ->
                val email = user.email
                if (!email.isNullOrBlank()) {
                    mailService.sendWeeklyReport(email, user.username ?: "User", latestTrends)
                }
            }
        }
    }
}
