package com.example.demo.service

import dev.samstevens.totp.code.CodeVerifier
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.secret.SecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.time.TimeProvider
import org.springframework.stereotype.Service

@Service
class MfaService {

    private val secretGenerator: SecretGenerator = DefaultSecretGenerator()
    private val timeProvider: TimeProvider = SystemTimeProvider()
    private val codeGenerator = DefaultCodeGenerator()
    private val codeVerifier: CodeVerifier = DefaultCodeVerifier(codeGenerator, timeProvider)

    // QrDataFactory and QrGenerator are usually dependent on implementation (e.g. Zxing)
    // For simplicity, we will return the "otpauth://" URI string for frontend to render.
    // Ideally we inject them, but let's keep it simple and standard.

    fun generateSecret(): String {
        return secretGenerator.generate()
    }

    fun getQrCodeUri(secret: String, accountName: String): String {
        val data =
                QrData.Builder()
                        .label(accountName)
                        .secret(secret)
                        .issuer("AIBlog")
                        .algorithm(HashingAlgorithm.SHA1)
                        .digits(6)
                        .period(30)
                        .build()

        return data.uri
    }

    fun verifyOtp(secret: String, code: String): Boolean {
        // Allow time drift of +/- 1 window (30 seconds)
        // Note: DefaultCodeVerifier uses system time.
        return codeVerifier.isValidCode(secret, code)
    }
}
