package com.example.demo.scoring

data class ResumeScoringResult(
    val totalScore: Int = 0,
    val skillScore: Int = 0,
    val experienceScore: Int = 0,
    val educationScore: Int = 0,
    val extractedSkills: String = "",
    val extractedExperience: String = "",
    val extractedEducation: String = "",
    val summary: String = ""
)
