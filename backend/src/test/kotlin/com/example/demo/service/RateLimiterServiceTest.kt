package com.example.demo.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RateLimiterServiceTest {

    private val rateLimiterService = RateLimiterService()

    @Test
    fun `should allow 20 requests and reject 21st`() {
        val key = "127.0.0.1"
        val bucket = rateLimiterService.resolveBucket(key)

        // Consume 20 tokens
        for (i in 1..20) {
            assertTrue(bucket.tryConsume(1), "Request $i should be allowed")
        }

        // 21st should fail
        assertFalse(bucket.tryConsume(1), "Request 21 should be blocked")
    }
}
