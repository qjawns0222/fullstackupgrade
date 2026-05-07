package com.example.demo.abtest

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "ab_test_results")
data class AbTestResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "toggle_name", nullable = false, length = 100)
    val toggleName: String,

    @Column(name = "variant_name", nullable = false, length = 100)
    val variantName: String,

    @Column(name = "user_id", length = 100)
    val userId: String? = null,

    @Column(name = "session_id", length = 100)
    val sessionId: String? = null,

    @Column(name = "payload", columnDefinition = "TEXT")
    val payload: String? = null,

    @Column(name = "recorded_at", nullable = false)
    val recordedAt: LocalDateTime = LocalDateTime.now()
)
