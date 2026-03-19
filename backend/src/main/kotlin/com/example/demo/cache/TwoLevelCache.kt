package com.example.demo.cache

import com.github.benmanes.caffeine.cache.Caffeine
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.Callable
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * A composite two-level cache implementation.
 *
 * Read path:  L1 (Caffeine JVM-local) → L2 (Redis via RedisCacheWriter) → DB loader
 * Write path: value → L2 (Redis) → publish invalidation event → all nodes drop L1
 * Evict path: L2 evict → publish invalidation event → all nodes drop L1
 *
 * This eliminates Redis round-trips for hot entries while keeping all nodes consistent
 * through pub/sub-based L1 invalidation.
 */
class TwoLevelCache(
    private val name: String,
    private val redisCacheWriter: RedisCacheWriter,
    private val redisCacheConfig: RedisCacheConfiguration,
    private val invalidationPublisher: CacheInvalidationPublisher,
    l1MaxSize: Int,
    l1TtlSeconds: Long
) : Cache {

    private val log = LoggerFactory.getLogger(TwoLevelCache::class.java)

    private val l1: com.github.benmanes.caffeine.cache.Cache<Any, Any> =
        Caffeine.newBuilder()
            .maximumSize(l1MaxSize.toLong())
            .expireAfterWrite(Duration.ofSeconds(l1TtlSeconds))
            .recordStats()
            .build()

    private val keySerializer: StringRedisSerializer =
        StringRedisSerializer()

    private val valueSerializationPair = redisCacheConfig.valueSerializationPair

    private val ttl: Duration = redisCacheConfig.ttl

    // ─── Redis key helper ────────────────────────────────────────────────────

    private fun redisKey(key: Any): ByteArray =
        keySerializer.serialize("$name::$key")!!

    private fun serializeValue(value: Any?): ByteArray {
        val buffer: ByteBuffer = valueSerializationPair.write(value)
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun deserializeValue(bytes: ByteArray): Any? =
        valueSerializationPair.read(ByteBuffer.wrap(bytes))

    // ─── Cache implementation ─────────────────────────────────────────────────

    override fun getName(): String = name

    override fun getNativeCache(): Any = mapOf("l1" to l1, "l2" to redisCacheWriter)

    override fun get(key: Any): Cache.ValueWrapper? {
        // Try L1 first (nanosecond lookup, no network)
        val l1Value = l1.getIfPresent(key)
        if (l1Value != null) {
            log.debug("[{}] L1 hit for key={}", name, key)
            return if (l1Value === NULL_PLACEHOLDER) SimpleValueWrapper(null) else SimpleValueWrapper(l1Value)
        }

        // Fall through to L2 (Redis)
        return try {
            val bytes = redisCacheWriter.get(name, redisKey(key))
            if (bytes != null) {
                val value = deserializeValue(bytes)
                log.debug("[{}] L2 hit for key={}, populating L1", name, key)
                l1.put(key, value ?: NULL_PLACEHOLDER)
                SimpleValueWrapper(value)
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("[{}] L2 get failed for key={}, treating as cache miss", name, key, e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, type: Class<T>?): T? {
        return get(key)?.get() as T?
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> get(key: Any, valueLoader: Callable<T>): T? {
        val existing = get(key)
        if (existing != null) return existing.get() as T?

        // Load from source
        val loaded: T? = try {
            valueLoader.call()
        } catch (e: Exception) {
            throw Cache.ValueRetrievalException(key, valueLoader, e)
        }

        put(key, loaded)
        return loaded
    }

    override fun put(key: Any, value: Any?) {
        try {
            redisCacheWriter.put(name, redisKey(key), serializeValue(value), ttl)
        } catch (e: Exception) {
            log.warn("[{}] L2 put failed for key={}", name, key, e)
        }
        l1.invalidate(key)
        invalidationPublisher.publish(name, key.toString())
        log.debug("[{}] Put key={}, L1 invalidated cluster-wide", name, key)
    }

    override fun evict(key: Any) {
        try {
            redisCacheWriter.remove(name, redisKey(key))
        } catch (e: Exception) {
            log.warn("[{}] L2 evict failed for key={}", name, key, e)
        }
        l1.invalidate(key)
        invalidationPublisher.publish(name, key.toString())
        log.debug("[{}] Evicted key={}, L1 invalidated cluster-wide", name, key)
    }

    override fun clear() {
        try {
            redisCacheWriter.clean(name, "*".toByteArray())
        } catch (e: Exception) {
            log.warn("[{}] L2 clear failed", name, e)
        }
        l1.invalidateAll()
        invalidationPublisher.publish(name, "*")
        log.info("[{}] Cache cleared, L1 invalidated cluster-wide", name)
    }

    /** Invalidate a specific key from L1 only (called upon receiving pub/sub event from another node). */
    fun invalidateL1(key: String) {
        if (key == "*") {
            l1.invalidateAll()
        } else {
            l1.invalidate(key)
        }
    }

    fun l1Stats(): com.github.benmanes.caffeine.cache.stats.CacheStats = l1.stats()

    fun l1EstimatedSize(): Long = l1.estimatedSize()

    companion object {
        val NULL_PLACEHOLDER: Any = Object()
    }
}
