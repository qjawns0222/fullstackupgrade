package com.example.demo.encryption

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Converter
@Component
class EncryptedStringConverter(
    private val encryptionService: EncryptionService
) : AttributeConverter<String?, String?> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute.isNullOrBlank()) return attribute
        return try {
            encryptionService.encrypt(attribute)
        } catch (e: Exception) {
            log.error("[Encryption] Failed to encrypt field value", e)
            throw RuntimeException("Field encryption failed", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData.isNullOrBlank()) return dbData
        return try {
            encryptionService.decrypt(dbData)
        } catch (e: Exception) {
            log.warn("[Encryption] Failed to decrypt — returning raw value (may be legacy plaintext)")
            dbData
        }
    }
}
