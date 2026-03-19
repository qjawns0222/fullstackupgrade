package com.example.demo.cache

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Custom [CacheManager] that creates [TwoLevelCache] instances.
 *
 * Each cache region is backed by:
 *  - L1: Caffeine in-process cache (sub-millisecond reads, bounded size, short TTL)
 *  - L2: Redis distributed cache (shared across all nodes, longer TTL)
 *
 * L1 is kept consistent via Redis pub/sub invalidation messages coordinated through
 * [CacheInvalidationSubscriber] and [CacheInvalidationPublisher].
 */
class TwoLevelCacheManager(
    private val redisCacheWriter: RedisCacheWriter,
    private val invalidationPublisher: CacheInvalidationPublisher,
    private val props: TwoLevelCacheProperties
) : CacheManager {

    private val log = LoggerFactory.getLogger(TwoLevelCacheManager::class.java)
    private val cacheMap = ConcurrentHashMap<String, TwoLevelCache>()

    private fun buildRedisCacheConfig(ttlSeconds: Long): RedisCacheConfiguration =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(ttlSeconds))
            .disableCachingNullValues()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    GenericJackson2JsonRedisSerializer()
                )
            )

    override fun getCache(name: String): Cache? {
        return cacheMap.computeIfAbsent(name) { cacheName ->
            log.info("Creating TwoLevelCache region: {}", cacheName)
            TwoLevelCache(
                name = cacheName,
                redisCacheWriter = redisCacheWriter,
                redisCacheConfig = buildRedisCacheConfig(props.l2TtlSeconds),
                invalidationPublisher = invalidationPublisher,
                l1MaxSize = props.l1MaxSize,
                l1TtlSeconds = props.l1TtlSeconds
            )
        }
    }

    override fun getCacheNames(): Collection<String> = cacheMap.keys.toSet()

    /**
     * Invalidate a specific key in L1 for a given cache region.
     * Called by [CacheInvalidationSubscriber] upon receiving a pub/sub event.
     */
    fun invalidateL1(cacheName: String, key: String) {
        cacheMap[cacheName]?.invalidateL1(key)
    }

    /**
     * Expose L1 hit/miss stats for monitoring dashboard.
     */
    fun getCacheStats(): Map<String, CacheStatsSnapshot> {
        return cacheMap.entries.associate { (name, cache) ->
            name to CacheStatsSnapshot(
                cacheName = name,
                l1EstimatedSize = cache.l1EstimatedSize(),
                l1HitCount = cache.l1Stats().hitCount(),
                l1MissCount = cache.l1Stats().missCount(),
                l1HitRate = cache.l1Stats().hitRate()
            )
        }
    }
}

data class CacheStatsSnapshot(
    val cacheName: String,
    val l1EstimatedSize: Long,
    val l1HitCount: Long,
    val l1MissCount: Long,
    val l1HitRate: Double
)
