package com.example.demo.apichange

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "api_snapshots")
class ApiSnapshot(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val version: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val specJson: String,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
