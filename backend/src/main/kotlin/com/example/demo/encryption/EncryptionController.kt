package com.example.demo.encryption

import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/encryption")
class EncryptionController(
    private val keyRotationScheduler: KeyRotationScheduler,
    private val keyRotationHistoryRepository: KeyRotationHistoryRepository,
    private val encryptionService: EncryptionService
) {

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<EncryptionStatusResponse> {
        val histories = keyRotationHistoryRepository.findAll(
            Sort.by(Sort.Direction.DESC, "rotatedAt")
        )
        val lastRotation = histories.firstOrNull()
        return ResponseEntity.ok(
            EncryptionStatusResponse(
                algorithm = "AES-256-GCM",
                keyRotationEnabled = true,
                lastRotatedAt = lastRotation?.rotatedAt?.toString(),
                totalRotations = histories.count { it.status == RotationStatus.SUCCESS },
                failedRotations = histories.count { it.status == RotationStatus.FAILED },
                history = histories.take(10).map {
                    RotationHistoryDto(
                        id = it.id!!,
                        rotatedAt = it.rotatedAt.toString(),
                        keyCount = it.keyCount,
                        status = it.status.name,
                        errorMessage = it.errorMessage
                    )
                }
            )
        )
    }

    @PostMapping("/rotate")
    fun rotateNow(): ResponseEntity<RotationHistoryDto> {
        val result = keyRotationScheduler.rotateNow()
        return ResponseEntity.ok(
            RotationHistoryDto(
                id = result.id!!,
                rotatedAt = result.rotatedAt.toString(),
                keyCount = result.keyCount,
                status = result.status.name,
                errorMessage = result.errorMessage
            )
        )
    }

    @PostMapping("/verify")
    fun verifyEncryption(@RequestBody request: VerifyRequest): ResponseEntity<VerifyResponse> {
        val encrypted = encryptionService.encrypt(request.plaintext)
        val decrypted = encryptionService.decrypt(encrypted)
        return ResponseEntity.ok(
            VerifyResponse(
                original = request.plaintext,
                encrypted = encrypted,
                decrypted = decrypted,
                success = request.plaintext == decrypted
            )
        )
    }
}

data class EncryptionStatusResponse(
    val algorithm: String,
    val keyRotationEnabled: Boolean,
    val lastRotatedAt: String?,
    val totalRotations: Int,
    val failedRotations: Int,
    val history: List<RotationHistoryDto>
)

data class RotationHistoryDto(
    val id: Long,
    val rotatedAt: String,
    val keyCount: Int,
    val status: String,
    val errorMessage: String?
)

data class VerifyRequest(val plaintext: String)

data class VerifyResponse(
    val original: String,
    val encrypted: String,
    val decrypted: String,
    val success: Boolean
)
