package com.example.demo.scoring

import org.springframework.stereotype.Component

@Component
class JpaResumeScoreStore(private val repo: ResumeScoreRepository) : ResumeScoreStore {
    override fun save(score: ResumeScore) = repo.save(score)
    override fun findByAnalysisRequestId(analysisRequestId: Long) = repo.findByAnalysisRequestId(analysisRequestId)
    override fun findAll() = repo.findAll()
}
