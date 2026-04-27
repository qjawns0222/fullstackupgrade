package com.example.demo.scoring

interface ResumeScoreStore {
    fun save(score: ResumeScore): ResumeScore
    fun findByAnalysisRequestId(analysisRequestId: Long): List<ResumeScore>
    fun findAll(): List<ResumeScore>
}
