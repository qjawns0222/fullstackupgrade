package com.example.demo.cache

import com.example.demo.repository.ResumeRepository
import org.springframework.stereotype.Component

@Component
class JpaWarmupResumeStore(
    private val resumeRepository: ResumeRepository
) : WarmupResumeStore {

    override fun findAllIds(): List<Long> =
        resumeRepository.findAll().mapNotNull { it.id }

    override fun countAll(): Long =
        resumeRepository.count()
}
