package com.example.demo.encryption

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetManager
import com.google.crypto.tink.aead.AeadKeyTemplates
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class KeyRotationScheduler(
    private val tinkConfig: TinkConfig,
    private val keyRotationHistoryRepository: KeyRotationHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${encryption.keyset-redis-key:tink:keyset:primary}") private val keysetRedisKey: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *")
    fun rotateKey() {
        log.info("[KeyRotation] Starting scheduled key rotation at {}", LocalDateTime.now())
        performRotation()
    }

    fun rotateNow(): KeyRotationHistory {
        log.info("[KeyRotation] Manual key rotation triggered")
        return performRotation()
    }

    private fun performRotation(): KeyRotationHistory {
        return try {
            val existingJson = redisTemplate.opsForValue().get(keysetRedisKey)
                ?: throw IllegalStateException("No existing keyset found in Redis")

            val existingHandle = CleartextKeysetHandle.read(JsonKeysetReader.withString(existingJson))

            // 새 키 추가 후 primary 승격 — Tink 1.11.0 KeysetManager API
            val rotatedHandle = KeysetManager
                .withKeysetHandle(existingHandle)
                .rotate(AeadKeyTemplates.AES256_GCM)
                .keysetHandle

            tinkConfig.saveKeysetToRedis(rotatedHandle)

            // keyset 내부 키 수는 proto 직접 접근 없이 직렬화 후 재파싱해서 확인
            val keyCount = countKeysInHandle(rotatedHandle)

            val history = KeyRotationHistory(
                rotatedAt = LocalDateTime.now(),
                keyCount = keyCount,
                status = RotationStatus.SUCCESS
            )
            keyRotationHistoryRepository.save(history).also {
                log.info("[KeyRotation] Completed. keyCount={}", keyCount)
            }
        } catch (e: Exception) {
            log.error("[KeyRotation] Failed", e)
            val history = KeyRotationHistory(
                rotatedAt = LocalDateTime.now(),
                keyCount = 0,
                status = RotationStatus.FAILED,
                errorMessage = e.message
            )
            keyRotationHistoryRepository.save(history)
        }
    }

    /**
     * Tink 1.11.0에서 keyset 필드가 private이므로,
     * 직렬화된 JSON을 파싱해 키 수를 추출합니다.
     */
    private fun countKeysInHandle(handle: KeysetHandle): Int {
        return try {
            val baos = java.io.ByteArrayOutputStream()
            CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(baos))
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val node = mapper.readTree(baos.toString(Charsets.UTF_8))
            node.get("key")?.size() ?: 1
        } catch (e: Exception) {
            -1
        }
    }
}
