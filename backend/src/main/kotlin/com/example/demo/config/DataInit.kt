package com.example.demo.config

import com.example.demo.entity.Resume
import com.example.demo.entity.TrendStats
import com.example.demo.entity.User
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.TrendStatsRepository
import com.example.demo.repository.UserRepository
import jakarta.annotation.PostConstruct
import java.time.LocalDateTime
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class DataInit(
        private val userRepository: UserRepository,
        private val trendStatsRepository: TrendStatsRepository,
        private val resumeRepository: ResumeRepository,
        private val passwordEncoder: PasswordEncoder
) {

    @PostConstruct
    fun init() {
        // 1. Create Test User
        var user: User? = userRepository.findByUsername("testuser1234").orElse(null)
        if (user == null) {
            user =
                    User(
                            username = "testuser1234",
                            password = passwordEncoder.encode("222222"),
                            role = "ROLE_USER"
                    )
            userRepository.save(user)
        }

        // 2. Create Dummy Resumes (1000 items for Batch Chunk Test)
        if (resumeRepository.count() < 1000) {
            val techStacks =
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
            val resumes =
                    (1..1000).map { i ->
                        val randomTechs =
                                techStacks.shuffled().take((1..3).random()).joinToString(" ")
                        Resume(
                                originalFileName = "dummy_resume_$i.pdf",
                                content =
                                        "This resume contains skills: $randomTechs and other details...",
                                user = user!!
                        )
                    }
            resumeRepository.saveAll(resumes)
            println("=== Dummy Resumes Created: ${resumes.size} (Chunk Test Ready) ===")
        }

        // 3. Create Dummy TrendStats Data (For Immediate Frontend Viz)
        if (trendStatsRepository.count() == 0L) {
            val techStacks =
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
            val stats =
                    techStacks.map { tech ->
                        TrendStats(
                                techStack = tech,
                                count = (10..100).random().toLong(),
                                recordedAt = LocalDateTime.now()
                        )
                    }
            trendStatsRepository.saveAll(stats)
            println("=== Dummy TrendStats Created ===")
        }
    }
}
