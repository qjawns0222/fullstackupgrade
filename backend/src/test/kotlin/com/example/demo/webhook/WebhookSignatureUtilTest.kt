package com.example.demo.webhook

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WebhookSignatureUtilTest {

    @Test
    fun `sign produces sha256 prefixed hex string`() {
        val signature = WebhookSignatureUtil.sign("hello world", "mysecret")
        assertTrue(signature.startsWith("sha256="), "Signature should start with 'sha256='")
        assertEquals(71, signature.length, "sha256= prefix (7) + 64 hex chars = 71")
    }

    @Test
    fun `verify returns true for matching payload and secret`() {
        val payload = """{"eventType":"TEST","data":{}}"""
        val secret = "supersecret"
        val sig = WebhookSignatureUtil.sign(payload, secret)
        assertTrue(WebhookSignatureUtil.verify(payload, secret, sig))
    }

    @Test
    fun `verify returns false for tampered payload`() {
        val secret = "supersecret"
        val sig = WebhookSignatureUtil.sign("""{"eventType":"TEST"}""", secret)
        assertFalse(WebhookSignatureUtil.verify("""{"eventType":"TAMPERED"}""", secret, sig))
    }

    @Test
    fun `verify returns false for wrong secret`() {
        val payload = """{"eventType":"TEST"}"""
        val sig = WebhookSignatureUtil.sign(payload, "correctsecret")
        assertFalse(WebhookSignatureUtil.verify(payload, "wrongsecret", sig))
    }

    @Test
    fun `same payload and secret always produce same signature`() {
        val payload = "deterministic test"
        val secret = "key"
        assertEquals(
            WebhookSignatureUtil.sign(payload, secret),
            WebhookSignatureUtil.sign(payload, secret)
        )
    }
}
