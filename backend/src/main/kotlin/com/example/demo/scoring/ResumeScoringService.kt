package com.example.demo.scoring

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ResumeScoringService(
    private val llmClient: LlmScoringClient,
    private val store: ResumeScoreStore,
) {
    private val log = LoggerFactory.getLogger(ResumeScoringService::class.java)

    fun score(analysisRequestId: Long, resumeText: String, jobTitle: String): ResumeScore {
        log.info("Scoring resume for requestId={} jobTitle={}", analysisRequestId, jobTitle)

        val result = llmClient.requestScoring(resumeText, jobTitle)

        val score = ResumeScore(
            analysisRequestId = analysisRequestId,
            jobTitle = jobTitle,
            totalScore = result.totalScore.coerceIn(0, 100),
            skillScore = result.skillScore.coerceIn(0, 100),
            experienceScore = result.experienceScore.coerceIn(0, 100),
            educationScore = result.educationScore.coerceIn(0, 100),
            extractedSkills = result.extractedSkills.takeIf { it.isNotBlank() },
            extractedExperience = result.extractedExperience.takeIf { it.isNotBlank() },
            extractedEducation = result.extractedEducation.takeIf { it.isNotBlank() },
            summary = result.summary.takeIf { it.isNotBlank() },
        )

        return store.save(score).also {
            log.info("Resume scored: requestId={} totalScore={}", analysisRequestId, it.totalScore)
        }
    }

    fun getScores(analysisRequestId: Long): List<ResumeScore> =
        store.findByAnalysisRequestId(analysisRequestId)

    fun getAllScores(): List<ResumeScore> = store.findAll()
}
