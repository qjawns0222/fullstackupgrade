package com.example.demo.entity

import com.example.demo.encryption.EncryptedStringConverter
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "resumes")
class Resume(
    @Column(nullable = false)
    var originalFileName: String,

    @Convert(converter = EncryptedStringConverter::class)
    @Column(columnDefinition = "TEXT")
    var content: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    var createdAt: LocalDateTime = LocalDateTime.now()
}
