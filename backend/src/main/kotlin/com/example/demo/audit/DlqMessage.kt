package com.example.demo.audit

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "dlq_messages")
class DlqMessage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 50)
    val userId: String,

    @Column(nullable = false, length = 100)
    val action: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val description: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val params: String,

    @Column(nullable = false, length = 20)
    val status: String,

    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(nullable = false)
    val originalTimestamp: LocalDateTime,

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var dlqStatus: DlqStatus = DlqStatus.PENDING,

    @Column(nullable = false)
    val failedAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var resolvedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Column(columnDefinition = "TEXT")
    var lastError: String? = null
)

enum class DlqStatus {
    PENDING,    // DLQ에 적재된 상태
    RETRYING,   // 재처리 시도 중
    RESOLVED,   // 재처리 성공
    DISCARDED   // 수동 폐기
}
