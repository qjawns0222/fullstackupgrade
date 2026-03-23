package com.example.demo.webhook

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utility for computing and verifying HMAC-SHA256 webhook signatures.
 *
 * The signature is included in the X-Webhook-Signature header as:
 *   sha256=<hex-encoded-hmac>
 *
 * Recipients should compute the same HMAC over the raw request body
 * with their registered secret and compare constant-time to prevent timing attacks.
 */
object WebhookSignatureUtil {

    private const val ALGORITHM = "HmacSHA256"
    private const val PREFIX = "sha256="

    fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val rawHmac = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return PREFIX + rawHmac.joinToString("") { "%02x".format(it) }
    }

    fun verify(payload: String, secret: String, signatureHeader: String): Boolean {
        val expected = sign(payload, secret)
        if (expected.length != signatureHeader.length) return false
        // Constant-time comparison
        var diff = 0
        for (i in expected.indices) {
            diff = diff or (expected[i].code xor signatureHeader[i].code)
        }
        return diff == 0
    }
}
