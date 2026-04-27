package com.example.demo.scoring

import org.springframework.data.jpa.repository.JpaRepository

interface ResumeScoreRepository : JpaRepository<ResumeScore, Long> {
    fun findByAnalysisRequestId(analysisRequestId: Long): List<ResumeScore>
}
