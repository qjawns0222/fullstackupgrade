package com.example.demo.encryption

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class EncryptionService(private val keysetHandle: KeysetHandle) {

    private val aead: Aead = keysetHandle.getPrimitive(Aead::class.java)
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encrypt(plaintext: String): String {
        val ciphertext = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), null)
        return encoder.encodeToString(ciphertext)
    }

    fun decrypt(ciphertext: String): String {
        val decoded = decoder.decode(ciphertext)
        val plaintext = aead.decrypt(decoded, null)
        return String(plaintext, Charsets.UTF_8)
    }

    fun isEncrypted(value: String): Boolean {
        return try {
            val decoded = decoder.decode(value)
            aead.decrypt(decoded, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
