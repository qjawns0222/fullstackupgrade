package com.example.demo.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "trend_stats")
class TrendStats(
        @Column(nullable = false) var techStack: String,
        @Column(nullable = false) var count: Long,
        @Column(nullable = false) var recordedAt: LocalDateTime = LocalDateTime.now()
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
}
