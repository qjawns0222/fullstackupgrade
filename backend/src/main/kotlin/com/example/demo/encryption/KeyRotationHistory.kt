package com.example.demo.encryption

import jakarta.persistence.*
import java.time.LocalDateTime

enum class RotationStatus { SUCCESS, FAILED }

@Entity
@Table(name = "key_rotation_history")
class KeyRotationHistory(
    @Column(nullable = false)
    val rotatedAt: LocalDateTime,

    @Column(nullable = false)
    val keyCount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RotationStatus,

    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}
