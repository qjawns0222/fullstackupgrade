package com.example.demo.saga

enum class ResumeSagaStatus {
    STARTED,
    S3_UPLOADED,
    DB_SAVED,
    ES_INDEXED,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}

data class ResumeSagaState(
    val sagaId: String,
    val userId: Long,
    val originalFileName: String,
    var status: ResumeSagaStatus = ResumeSagaStatus.STARTED,
    var s3FileKey: String? = null,
    var resumeId: Long? = null,
    var errorMessage: String? = null,
    var createdAt: Long = System.currentTimeMillis()
)
