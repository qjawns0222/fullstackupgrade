package com.example.demo.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis pub/sub based implementation of [CacheInvalidationPublisher].
 *
 * Publishes messages to a Redis channel. All application nodes subscribe to this channel
 * and upon receiving a message they drop the corresponding key from their local L1 cache.
 *
 * Message format: "{cacheName}:{key}"
 */
@Component
class RedisCacheInvalidationPublisher(
    private val redisTemplate: StringRedisTemplate,
    private val props: TwoLevelCacheProperties
) : CacheInvalidationPublisher {

    private val log = LoggerFactory.getLogger(RedisCacheInvalidationPublisher::class.java)

    override fun publish(cacheName: String, key: String) {
        try {
            val message = "$cacheName:$key"
            redisTemplate.convertAndSend(props.invalidationTopic, message)
            log.debug("Published cache invalidation: topic={} message={}", props.invalidationTopic, message)
        } catch (e: Exception) {
            // Pub/sub failure must NOT break application flow — L1 will expire naturally via TTL
            log.warn("Failed to publish cache invalidation event for {}:{} — L1 will expire via TTL", cacheName, key, e)
        }
    }
}
