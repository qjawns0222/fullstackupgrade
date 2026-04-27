package com.example.demo.scoring

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.stereotype.Component

@Component
class SpringAiScoringClient(private val chatClient: ChatClient) : LlmScoringClient {

    private val log = LoggerFactory.getLogger(SpringAiScoringClient::class.java)
    private val converter = BeanOutputConverter(ResumeScoringResult::class.java)

    override fun requestScoring(resumeText: String, jobTitle: String): ResumeScoringResult {
        val format = converter.format

        val prompt = """
            You are an expert HR recruiter. Analyze the following resume text and evaluate it for the job title: "$jobTitle".

            Resume Text:
            ---
            $resumeText
            ---

            Respond ONLY with a JSON object matching this format:
            $format

            Score rules:
            - totalScore: weighted average (skills 40%, experience 40%, education 20%), range 0-100
            - skillScore: how well skills match the job, range 0-100
            - experienceScore: relevance and depth of experience, range 0-100
            - educationScore: education level and relevance, range 0-100
            - extractedSkills: comma-separated list of skills found
            - extractedExperience: brief summary of experience
            - extractedEducation: brief summary of education
            - summary: 2-3 sentence overall assessment
        """.trimIndent()

        val response = chatClient.prompt(prompt).call().content()
            ?: throw IllegalStateException("LLM returned null response")

        return runCatching { converter.convert(response) }
            .getOrElse {
                log.warn("Failed to parse LLM response, using defaults. response={}", response)
                ResumeScoringResult()
            }
    }
}
