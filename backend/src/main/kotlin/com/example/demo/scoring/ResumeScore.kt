package com.example.demo.scoring

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "resume_scores")
class ResumeScore(
    @Column(nullable = false) val analysisRequestId: Long,
    @Column(nullable = false) val jobTitle: String,
    @Column(nullable = false) val totalScore: Int,
    @Column(nullable = false) val skillScore: Int,
    @Column(nullable = false) val experienceScore: Int,
    @Column(nullable = false) val educationScore: Int,
    @Column(columnDefinition = "TEXT") val extractedSkills: String?,
    @Column(columnDefinition = "TEXT") val extractedExperience: String?,
    @Column(columnDefinition = "TEXT") val extractedEducation: String?,
    @Column(columnDefinition = "TEXT") val summary: String?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    val createdAt: LocalDateTime = LocalDateTime.now()
}
