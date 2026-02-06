package com.example.demo.service

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MfaServiceTest {

    private val mfaService = MfaService()

    @Test
    fun `should generate secret and verify valid code`() {
        // Given
        val secret = mfaService.generateSecret()
        assertNotNull(secret)
        assertTrue(secret.isNotEmpty())

        // When
        val generator = DefaultCodeGenerator()
        val timeProvider = SystemTimeProvider()

        // Generate a valid code for current time
        // Note: DefaultCodeVerifier uses System blocks (30s)
        val currentBucket = Math.floor(timeProvider.time.toDouble() / 30).toLong()
        val code = generator.generate(secret, currentBucket)

        // Then
        assertTrue(mfaService.verifyOtp(secret, code))
    }

    @Test
    fun `should reject invalid code`() {
        val secret = mfaService.generateSecret()
        val invalidCode = "000000"
        assertFalse(mfaService.verifyOtp(secret, invalidCode))
    }
}
