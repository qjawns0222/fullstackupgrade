package com.example.demo.cache

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Configuration properties for the two-level cache (L1 JVM-local + L2 Redis).
 *
 * L1 reduces Redis round-trips by keeping hot entries locally per JVM instance.
 * L2 (Redis) acts as the distributed shared store and coordinates invalidation
 * across all nodes via pub/sub so L1 caches never serve stale data.
 */
@Component
@ConfigurationProperties(prefix = "cache.two-level")
data class TwoLevelCacheProperties(
    /** Maximum number of entries to keep in L1 (per cache region, per JVM node). */
    var l1MaxSize: Int = 200,

    /** Time-to-live for L1 entries in seconds. Should be shorter than L2 TTL. */
    var l1TtlSeconds: Long = 30,

    /** Time-to-live for L2 (Redis) entries in seconds. */
    var l2TtlSeconds: Long = 300,

    /**
     * Redis pub/sub topic name used to broadcast L1 invalidation events across nodes.
     * When any node evicts or updates a key, it publishes to this topic so all
     * other nodes immediately drop their L1 copy.
     */
    var invalidationTopic: String = "cache:invalidation",

    /** Cache region names managed by the two-level cache manager. */
    var cacheNames: List<String> = listOf("dashboard", "jobApplications", "resumeList", "trendStats")
)
