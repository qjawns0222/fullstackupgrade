package com.example.demo.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.never
import org.mockito.Mockito.anyString
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.any
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterTokenBlacklistServiceTest {

    @Mock
    private lateinit var redisTemplate: StringRedisTemplate

    @Mock
    private lateinit var valueOps: ValueOperations<String, String>

    private fun createService(): BloomFilterTokenBlacklistService {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
        return BloomFilterTokenBlacklistService(redisTemplate)
    }

    @Test
    fun `blacklist - stores token hash in Redis with correct TTL`() {
        val service = createService()
        val token = "eyJhbGciOiJIUzI1NiJ9.testPayload.signature"
        val ttl = 1800L

        service.blacklist(token, ttl)

        verify(valueOps, times(1)).set(
            org.mockito.ArgumentMatchers.startsWith("token:blacklist:"),
            org.mockito.ArgumentMatchers.eq("1"),
            org.mockito.ArgumentMatchers.eq(ttl),
            org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)
        )
    }

    @Test
    fun `blacklist - skips storage when TTL is zero or negative`() {
        val token = "already.expired.token"
        // Need a fresh service without opsForValue stub since it won't be called
        val freshRedisTemplate = org.mockito.Mockito.mock(StringRedisTemplate::class.java)
        val service = BloomFilterTokenBlacklistService(freshRedisTemplate)

        service.blacklist(token, 0L)
        service.blacklist(token, -100L)

        verify(freshRedisTemplate, never()).opsForValue()
    }

    @Test
    fun `isBlacklisted - returns false without hitting Redis when token is not in bloom filter`() {
        // Need a fresh service so bloom filter is empty
        val freshRedisTemplate = org.mockito.Mockito.mock(StringRedisTemplate::class.java)
        val service = BloomFilterTokenBlacklistService(freshRedisTemplate)
        val token = "fresh.valid.token.not.in.bloom"

        val result = service.isBlacklisted(token)

        assertFalse(result)
        verify(freshRedisTemplate, never()).hasKey(anyString())
    }

    @Test
    fun `isBlacklisted - returns true when token is in bloom filter AND confirmed in Redis`() {
        val service = createService()
        val token = "blacklisted.access.token.xyz"
        val ttl = 900L

        service.blacklist(token, ttl)

        `when`(redisTemplate.hasKey(org.mockito.ArgumentMatchers.startsWith("token:blacklist:")))
            .thenReturn(true)

        val result = service.isBlacklisted(token)

        assertTrue(result)
        verify(redisTemplate, times(1)).hasKey(org.mockito.ArgumentMatchers.startsWith("token:blacklist:"))
    }

    @Test
    fun `isBlacklisted - returns false when bloom filter positive but Redis confirms absence`() {
        val service = createService()
        val token = "bloom.false.positive.token"
        val ttl = 900L

        service.blacklist(token, ttl)

        `when`(redisTemplate.hasKey(org.mockito.ArgumentMatchers.startsWith("token:blacklist:")))
            .thenReturn(false)

        val result = service.isBlacklisted(token)

        assertFalse(result)
    }

    @Test
    fun `same token always produces same Redis key (deterministic hashing)`() {
        val service = createService()
        val token = "stable.token.abc123"
        val ttl = 600L

        service.blacklist(token, ttl)
        service.blacklist(token, ttl)

        verify(valueOps, times(2)).set(
            org.mockito.ArgumentMatchers.startsWith("token:blacklist:"),
            org.mockito.ArgumentMatchers.eq("1"),
            org.mockito.ArgumentMatchers.eq(ttl),
            org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)
        )
    }

    @Test
    fun `different tokens produce different Redis keys`() {
        val service = createService()
        val tokenA = "token.alpha.123"
        val tokenB = "token.beta.456"
        val ttl = 600L

        val capturedKeys = mutableListOf<String>()
        `when`(valueOps.set(
            anyString(),
            anyString(),
            anyLong(),
            any()
        )).then { invocation ->
            capturedKeys.add(invocation.getArgument(0))
            null
        }

        service.blacklist(tokenA, ttl)
        service.blacklist(tokenB, ttl)

        assertEquals(2, capturedKeys.size)
        assertNotEquals(capturedKeys[0], capturedKeys[1])
    }
}
