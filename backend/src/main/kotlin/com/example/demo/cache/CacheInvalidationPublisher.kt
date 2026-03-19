package com.example.demo.cache

/**
 * Abstraction for broadcasting cache invalidation events to other cluster nodes.
 * When a node writes or evicts a key, it calls this publisher so every other node
 * can immediately drop its L1 copy — preventing stale reads in a multi-instance setup.
 */
interface CacheInvalidationPublisher {
    /**
     * Publish an invalidation event for a specific key in a cache region.
     * @param cacheName the logical cache region name (e.g., "dashboard")
     * @param key       the serialized cache key, or "*" to invalidate the entire region
     */
    fun publish(cacheName: String, key: String)
}
