package com.example.demo.analysis

import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import java.nio.file.Files
import net.sourceforge.tess4j.ITesseract
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import org.springframework.data.redis.core.StringRedisTemplate
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Service
open class OcrService(
    private val tesseract: ITesseract,
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(OcrService::class.java)
    private val CACHE_PREFIX = "ocr:cache:"

    @Bulkhead(name = "ocrService")
    @CircuitBreaker(name = "ocrService")
    open fun doOcr(data: ByteArray): String {
        log.info("Starting OCR processing for file of size: {} bytes", data.size)

        // 1. Generate Hash
        val hash = generateHash(data)
        val cacheKey = "$CACHE_PREFIX$hash"

        // 2. Check Cache
        val cachedResult = redisTemplate.opsForValue().get(cacheKey)
        if (cachedResult != null) {
            log.info("OCR cache hit for hash: {}", hash)
            return cachedResult
        }

        // 3. Perform OCR
        val tempFile = Files.createTempFile("ocr_", ".tmp").toFile()
        try {
            tempFile.writeBytes(data)
            val result = tesseract.doOCR(tempFile)
            
            // 4. Update Cache (TTL: 7 days)
            redisTemplate.opsForValue().set(cacheKey, result, 7, TimeUnit.DAYS)
            
            log.info("OCR processing completed and cached for hash: {}", hash)
            return result
        } catch (e: Exception) {
            log.error("OCR processing failed", e)
            throw RuntimeException("Failed to perform OCR: ${e.message}", e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun generateHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
