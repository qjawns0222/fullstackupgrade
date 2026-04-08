package com.example.demo.apichange

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "api_breaking_changes")
class ApiBreakingChange(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val oldVersion: String,

    @Column(nullable = false, length = 50)
    val newVersion: String,

    @Column(nullable = false, length = 100)
    val changeType: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val description: String,

    @Column(length = 500)
    val element: String? = null,

    @Column(nullable = false)
    val detectedAt: LocalDateTime = LocalDateTime.now()
)
