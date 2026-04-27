package com.example.demo.scoring

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResumeScoringServiceTest {

    private lateinit var store: FakeResumeScoreStore
    private lateinit var llmClient: FakeLlmScoringClient
    private lateinit var service: ResumeScoringService

    @BeforeEach
    fun setUp() {
        store = FakeResumeScoreStore()
        llmClient = FakeLlmScoringClient()
        service = ResumeScoringService(llmClient, store)
    }

    @Test
    fun `score - saves result with correct fields`() {
        llmClient.nextResult = ResumeScoringResult(
            totalScore = 85,
            skillScore = 90,
            experienceScore = 80,
            educationScore = 75,
            extractedSkills = "Kotlin, Spring Boot",
            extractedExperience = "5 years backend",
            extractedEducation = "BS Computer Science",
            summary = "Strong backend candidate.",
        )

        val result = service.score(1L, "Kotlin developer resume text", "Backend Engineer")

        assertEquals(1L, result.analysisRequestId)
        assertEquals("Backend Engineer", result.jobTitle)
        assertEquals(85, result.totalScore)
        assertEquals(90, result.skillScore)
        assertEquals(80, result.experienceScore)
        assertEquals(75, result.educationScore)
        assertEquals("Kotlin, Spring Boot", result.extractedSkills)
        assertEquals("Strong backend candidate.", result.summary)
        assertTrue(store.savedScores.isNotEmpty())
    }

    @Test
    fun `score - clamps totalScore over 100 to 100`() {
        llmClient.nextResult = ResumeScoringResult(totalScore = 150, skillScore = 100, experienceScore = 100, educationScore = 100)

        val result = service.score(2L, "resume", "Engineer")

        assertEquals(100, result.totalScore)
    }

    @Test
    fun `score - clamps negative skillScore to 0`() {
        llmClient.nextResult = ResumeScoringResult(totalScore = 50, skillScore = -10, experienceScore = 60, educationScore = 70)

        val result = service.score(3L, "resume", "Engineer")

        assertEquals(0, result.skillScore)
    }

    @Test
    fun `score - blank extractedSkills saved as null`() {
        llmClient.nextResult = ResumeScoringResult(totalScore = 60, extractedSkills = "")

        val result = service.score(4L, "resume", "Engineer")

        assertNull(result.extractedSkills)
    }

    @Test
    fun `getScores - returns only scores for given analysisRequestId`() {
        store.save(makeScore(analysisRequestId = 10L))
        store.save(makeScore(analysisRequestId = 10L))
        store.save(makeScore(analysisRequestId = 99L))

        val scores = service.getScores(10L)

        assertEquals(2, scores.size)
        assertTrue(scores.all { it.analysisRequestId == 10L })
    }

    @Test
    fun `getAllScores - returns all saved scores`() {
        store.save(makeScore(analysisRequestId = 1L))
        store.save(makeScore(analysisRequestId = 2L))

        assertEquals(2, service.getAllScores().size)
    }

    private fun makeScore(analysisRequestId: Long) = ResumeScore(
        analysisRequestId = analysisRequestId,
        jobTitle = "Engineer",
        totalScore = 70,
        skillScore = 70,
        experienceScore = 70,
        educationScore = 70,
        extractedSkills = "Kotlin",
        extractedExperience = "3 years",
        extractedEducation = "BS",
        summary = "Good candidate",
    )
}

class FakeLlmScoringClient : LlmScoringClient {
    var nextResult: ResumeScoringResult = ResumeScoringResult()

    override fun requestScoring(resumeText: String, jobTitle: String): ResumeScoringResult = nextResult
}

class FakeResumeScoreStore : ResumeScoreStore {
    val savedScores = mutableListOf<ResumeScore>()
    private var idSeq = 1L

    override fun save(score: ResumeScore): ResumeScore {
        score.id = idSeq++
        savedScores.add(score)
        return score
    }

    override fun findByAnalysisRequestId(analysisRequestId: Long) =
        savedScores.filter { it.analysisRequestId == analysisRequestId }

    override fun findAll() = savedScores.toList()
}
