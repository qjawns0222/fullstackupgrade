package com.example.demo.encryption

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class EncryptionServiceTest {

    companion object {
        private lateinit var encryptionService: EncryptionService

        @JvmStatic
        @BeforeAll
        fun setup() {
            AeadConfig.register()
            val handle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
            encryptionService = EncryptionService(handle)
        }
    }

    @Test
    fun `encrypt and decrypt roundtrip`() {
        val plaintext = "민감한 이메일 주소: test@example.com"
        val encrypted = encryptionService.encrypt(plaintext)
        val decrypted = encryptionService.decrypt(encrypted)

        assertThat(encrypted).isNotEqualTo(plaintext)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `same plaintext produces different ciphertext each time (probabilistic encryption)`() {
        val plaintext = "동일한 입력"
        val enc1 = encryptionService.encrypt(plaintext)
        val enc2 = encryptionService.encrypt(plaintext)

        // AES-GCM uses random IV so ciphertext differs each call
        assertThat(enc1).isNotEqualTo(enc2)
        assertThat(encryptionService.decrypt(enc1)).isEqualTo(plaintext)
        assertThat(encryptionService.decrypt(enc2)).isEqualTo(plaintext)
    }

    @Test
    fun `isEncrypted returns true for encrypted value`() {
        val encrypted = encryptionService.encrypt("test value")
        assertThat(encryptionService.isEncrypted(encrypted)).isTrue()
    }

    @Test
    fun `isEncrypted returns false for plaintext`() {
        assertThat(encryptionService.isEncrypted("plaintext@email.com")).isFalse()
    }

    @Test
    fun `encrypt empty string`() {
        val encrypted = encryptionService.encrypt("")
        val decrypted = encryptionService.decrypt(encrypted)
        assertThat(decrypted).isEqualTo("")
    }

    @Test
    fun `encrypt Korean characters`() {
        val plaintext = "홍길동의 이력서 내용입니다. 특수문자 포함: !@#$%"
        val encrypted = encryptionService.encrypt(plaintext)
        val decrypted = encryptionService.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(plaintext)
    }
}
