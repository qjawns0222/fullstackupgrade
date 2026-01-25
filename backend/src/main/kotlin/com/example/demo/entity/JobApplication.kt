package com.example.demo.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class JobApplicationStatus {
    APPLIED,
    INTERVIEW,
    REJECTED,
    PASSED,
    OFFER_RECEIVED
}

@Entity
@Table(name = "job_applications")
class JobApplication(
        @Column(nullable = false) var companyName: String,
        @Column(nullable = false) var position: String,
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        var status: JobApplicationStatus = JobApplicationStatus.APPLIED,
        var appliedDate: LocalDate = LocalDate.now(),
        @Column(columnDefinition = "TEXT") var memo: String? = null,
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id") var user: User
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null

    var createdAt: LocalDateTime = LocalDateTime.now()
    var updatedAt: LocalDateTime = LocalDateTime.now()
}
