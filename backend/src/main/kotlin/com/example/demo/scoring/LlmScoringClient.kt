package com.example.demo.scoring

interface LlmScoringClient {
    fun requestScoring(resumeText: String, jobTitle: String): ResumeScoringResult
}
