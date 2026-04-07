package com.example.demo.encryption

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.io.ByteArrayOutputStream

@Configuration
open class TinkConfig(
    private val redisTemplate: StringRedisTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${encryption.keyset-redis-key:tink:keyset:primary}")
    private lateinit var keysetRedisKey: String

    init {
        AeadConfig.register()
    }

    @Bean
    fun keysetHandle(): KeysetHandle {
        val existing = redisTemplate.opsForValue().get(keysetRedisKey)
        return if (existing != null) {
            log.info("[Encryption] Loading existing keyset from Redis key=$keysetRedisKey")
            CleartextKeysetHandle.read(JsonKeysetReader.withString(existing))
        } else {
            log.info("[Encryption] No keyset found — generating new AES256_GCM keyset")
            val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
            saveKeysetToRedis(handle)
            handle
        }
    }

    open fun saveKeysetToRedis(handle: KeysetHandle) {
        val baos = ByteArrayOutputStream()
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withOutputStream(baos))
        redisTemplate.opsForValue().set(keysetRedisKey, baos.toString(Charsets.UTF_8))
        log.info("[Encryption] Keyset saved to Redis key=$keysetRedisKey")
    }
}
