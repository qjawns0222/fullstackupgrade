package com.example.demo.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "analysis_requests")
class AnalysisRequest(
    @Column(nullable = false)
    var originalFileName: String
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: Status = Status.PENDING

    @Column(columnDefinition = "TEXT")
    var result: String? = null

    var createdAt: LocalDateTime = LocalDateTime.now()

    enum class Status {
        PENDING,
        ANALYZING,
        COMPLETED,
        FAILED
    }

    fun startAnalysis() {
        this.status = Status.ANALYZING
    }

    fun complete(result: String) {
        this.status = Status.COMPLETED
        this.result = result
    }

    fun fail(errorMessage: String) {
        this.status = Status.FAILED
        this.result = errorMessage
    }
}
