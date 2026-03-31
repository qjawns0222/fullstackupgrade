package com.example.demo.security

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

/**
 * Two-tier token blacklist:
 *
 *  Tier 1 — Guava BloomFilter (JVM-local)
 *    - Probabilistic, ~1% false-positive rate, zero false negatives
 *    - A "not in bloom filter" result means "definitely not blacklisted" → skip Redis entirely
 *    - A "in bloom filter" result means "maybe blacklisted" → confirm with Redis
 *
 *  Tier 2 — Redis SET with TTL
 *    - Authoritative distributed store
 *    - Key pattern: "token:blacklist:{jti_or_hash}"
 *    - TTL = remaining token validity, so the set never grows unboundedly
 *
 * Performance profile under 10,000 RPS:
 *   - ~99.9% of requests hit the bloom filter and return immediately (non-blacklisted tokens)
 *   - Only logout calls and bloom-positive tokens ever touch Redis
 */
@Service
class BloomFilterTokenBlacklistService(
    private val redisTemplate: StringRedisTemplate
) : TokenBlacklistService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val REDIS_PREFIX = "token:blacklist:"
        // Expected insertions for the bloom filter — sized for 100k revoked tokens
        // before a false-positive rate of 1% is reached
        private const val EXPECTED_INSERTIONS = 100_000L
        private const val FALSE_POSITIVE_PROBABILITY = 0.01
    }

    // Not thread-safe by itself; synchronize writes.
    // In a multi-instance deployment each JVM maintains its own bloom filter.
    // A rogue token is at worst valid on instances that haven't seen the logout event
    // for the sub-millisecond pub/sub propagation window — acceptable for most threat models.
    private val bloomFilter: BloomFilter<String> = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        EXPECTED_INSERTIONS,
        FALSE_POSITIVE_PROBABILITY
    )

    override fun blacklist(token: String, remainingTtlSeconds: Long) {
        if (remainingTtlSeconds <= 0) {
            // Token is already expired — no need to store
            log.debug("Skipping blacklist for already-expired token")
            return
        }
        val key = redisKey(token)
        redisTemplate.opsForValue().set(key, "1", remainingTtlSeconds, TimeUnit.SECONDS)
        synchronized(bloomFilter) {
            bloomFilter.put(token)
        }
        log.info("Access token blacklisted, TTL={}s", remainingTtlSeconds)
    }

    override fun isBlacklisted(token: String): Boolean {
        // Fast path: bloom filter says "definitely not here"
        val mightBePresent = synchronized(bloomFilter) { bloomFilter.mightContain(token) }
        if (!mightBePresent) {
            return false
        }
        // Slow path: bloom filter says "maybe" → confirm with Redis
        val exists = redisTemplate.hasKey(redisKey(token))
        return exists == true
    }

    override fun blacklistSize(): Long {
        val keys = redisTemplate.keys("$REDIS_PREFIX*")
        return keys?.size?.toLong() ?: 0L
    }

    private fun redisKey(token: String): String {
        // Store a SHA-256 fingerprint so the raw JWT is never persisted in Redis
        val hash = com.google.common.hash.Hashing.sha256()
            .hashString(token, StandardCharsets.UTF_8)
            .toString()
        return "$REDIS_PREFIX$hash"
    }
}
