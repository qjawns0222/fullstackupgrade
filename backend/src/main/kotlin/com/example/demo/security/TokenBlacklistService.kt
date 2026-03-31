package com.example.demo.security

/**
 * Abstraction for JWT access token blacklisting.
 *
 * Problem: JWT access tokens remain valid for up to 30 minutes after logout or compromise.
 * There is no built-in revocation mechanism in stateless JWT — once issued, a token is valid
 * until expiry. Under large-scale environments (thousands of active sessions), a stolen
 * access token can be abused for the full validity window, and a password reset does nothing
 * to invalidate in-flight access tokens.
 *
 * Solution: Two-tier blacklist using a Guava BloomFilter (JVM-local, O(1)) as the first gate,
 * followed by a Redis SET (distributed truth) for confirmed revocation. The bloom filter
 * eliminates ~100% of Redis round-trips for non-blacklisted tokens (the common path), keeping
 * hot-path latency at sub-millisecond.
 */
interface TokenBlacklistService {

    /**
     * Adds an access token to the blacklist.
     * TTL should be set to the remaining validity time of the token so Redis does not
     * grow unboundedly.
     *
     * @param token raw JWT string
     * @param remainingTtlSeconds seconds until the token naturally expires
     */
    fun blacklist(token: String, remainingTtlSeconds: Long)

    /**
     * Returns true if the token has been explicitly revoked.
     * False DOES NOT guarantee the token is valid — signature/expiry checks are still required.
     */
    fun isBlacklisted(token: String): Boolean

    /**
     * Returns the total number of tokens currently blacklisted in Redis.
     * Used for monitoring / actuator metrics.
     */
    fun blacklistSize(): Long
}
