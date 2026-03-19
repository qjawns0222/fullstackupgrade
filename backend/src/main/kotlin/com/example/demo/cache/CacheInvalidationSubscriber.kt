package com.example.demo.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

/**
 * Redis pub/sub subscriber that receives cache invalidation events published by
 * other cluster nodes (or the current node) and drops the corresponding L1 entries.
 *
 * This is the key piece that makes the two-level cache safe in a clustered deployment:
 * without this, every JVM node's in-memory L1 would independently hold stale copies
 * after a write on any other node.
 */
@Component
class CacheInvalidationSubscriber(
    private val twoLevelCacheManager: TwoLevelCacheManager
) : MessageListener {

    private val log = LoggerFactory.getLogger(CacheInvalidationSubscriber::class.java)

    /**
     * Called by Spring Data Redis when a message arrives on the invalidation topic.
     * Message format: "{cacheName}:{key}"
     */
    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val body = String(message.body)
            val colonIdx = body.indexOf(':')
            if (colonIdx < 0) {
                log.warn("Malformed invalidation message: {}", body)
                return
            }
            val cacheName = body.substring(0, colonIdx)
            val key = body.substring(colonIdx + 1)

            twoLevelCacheManager.invalidateL1(cacheName, key)
            log.debug("L1 invalidated via pub/sub: cacheName={} key={}", cacheName, key)
        } catch (e: Exception) {
            log.error("Error processing cache invalidation message", e)
        }
    }
}
