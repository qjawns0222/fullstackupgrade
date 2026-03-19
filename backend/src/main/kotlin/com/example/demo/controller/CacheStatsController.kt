package com.example.demo.controller

import com.example.demo.cache.CacheStatsSnapshot
import com.example.demo.cache.TwoLevelCacheManager
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for observing the two-level cache runtime state.
 *
 * Exposes L1 hit rates per cache region so operators can tune
 * [TwoLevelCacheProperties.l1MaxSize] and [TwoLevelCacheProperties.l1TtlSeconds].
 */
@RestController
@RequestMapping("/api/cache")
class CacheStatsController(
    private val twoLevelCacheManager: TwoLevelCacheManager
) {

    /**
     * GET /api/cache/stats
     * Returns L1 hit/miss counts and hit-rate for each active cache region.
     */
    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, CacheStatsSnapshot>> {
        return ResponseEntity.ok(twoLevelCacheManager.getCacheStats())
    }

    /**
     * DELETE /api/cache/{cacheName}
     * Clears all entries in the given cache region (L1 + L2) and broadcasts
     * an invalidation event to all cluster nodes.
     */
    @DeleteMapping("/{cacheName}")
    fun clearCache(@PathVariable cacheName: String): ResponseEntity<Map<String, String>> {
        val cache = twoLevelCacheManager.getCache(cacheName)
            ?: return ResponseEntity.notFound().build()
        cache.clear()
        return ResponseEntity.ok(mapOf("message" to "Cache '$cacheName' cleared successfully"))
    }

    /**
     * DELETE /api/cache/{cacheName}/{key}
     * Evicts a single key from the given cache region.
     */
    @DeleteMapping("/{cacheName}/{key}")
    fun evictKey(
        @PathVariable cacheName: String,
        @PathVariable key: String
    ): ResponseEntity<Map<String, String>> {
        val cache = twoLevelCacheManager.getCache(cacheName)
            ?: return ResponseEntity.notFound().build()
        cache.evict(key)
        return ResponseEntity.ok(mapOf("message" to "Key '$key' evicted from cache '$cacheName'"))
    }
}
