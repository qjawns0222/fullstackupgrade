package com.example.demo.tracing

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "span_records")
class SpanRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 200)
    val spanName: String,

    @Column(nullable = false, length = 200)
    val className: String,

    @Column(nullable = false, length = 200)
    val methodName: String,

    @Column(nullable = false)
    val durationMs: Long,

    @Column(nullable = false, length = 20)
    val status: String,  // SUCCESS | ERROR | SLOW

    @Column(length = 500)
    val errorMessage: String? = null,

    @Column(nullable = false)
    val recordedAt: LocalDateTime = LocalDateTime.now()
)
