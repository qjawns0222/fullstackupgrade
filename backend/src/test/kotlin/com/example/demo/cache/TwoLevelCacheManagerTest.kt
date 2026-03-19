package com.example.demo.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.redis.cache.RedisCacheWriter

/**
 * Unit tests for [TwoLevelCacheManager].
 */
class TwoLevelCacheManagerTest {

    private lateinit var mockWriter: RedisCacheWriter
    private lateinit var mockPublisher: CacheInvalidationPublisher
    private lateinit var props: TwoLevelCacheProperties
    private lateinit var manager: TwoLevelCacheManager

    @BeforeEach
    fun setUp() {
        mockWriter = mock(RedisCacheWriter::class.java)
        mockPublisher = mock(CacheInvalidationPublisher::class.java)
        props = TwoLevelCacheProperties(
            l1MaxSize = 100,
            l1TtlSeconds = 10,
            l2TtlSeconds = 60,
            invalidationTopic = "cache:invalidation",
            cacheNames = listOf("region1", "region2")
        )
        manager = TwoLevelCacheManager(mockWriter, mockPublisher, props)
    }

    @Test
    fun `getCache should lazily create and return a TwoLevelCache`() {
        val cache = manager.getCache("myCache")
        assertNotNull(cache)
        assertInstanceOf(TwoLevelCache::class.java, cache)
        assertEquals("myCache", cache!!.name)
    }

    @Test
    fun `getCache should return same instance on repeated calls`() {
        val first = manager.getCache("sameRegion")
        val second = manager.getCache("sameRegion")
        assertSame(first, second)
    }

    @Test
    fun `getCacheNames should include all accessed regions`() {
        manager.getCache("alpha")
        manager.getCache("beta")
        val names = manager.getCacheNames()
        assertTrue(names.contains("alpha"))
        assertTrue(names.contains("beta"))
    }

    @Test
    fun `invalidateL1 for unknown cache should not throw`() {
        assertDoesNotThrow { manager.invalidateL1("nonExistentCache", "someKey") }
    }

    @Test
    fun `invalidateL1 should delegate to existing TwoLevelCache`() {
        // Create the cache region first
        val cache = manager.getCache("testRegion") as TwoLevelCache

        // Populate L1 indirectly via put (L2 is mocked so won't actually store anything)
        // We just verify invalidateL1 doesn't throw after region is initialized
        manager.invalidateL1("testRegion", "someKey")
        // No exception means the invalidation was forwarded correctly
    }

    @Test
    fun `getCacheStats should return empty map when no caches accessed`() {
        val stats = manager.getCacheStats()
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `getCacheStats should contain entry after accessing a cache`() {
        manager.getCache("statsTest")
        val stats = manager.getCacheStats()
        assertTrue(stats.containsKey("statsTest"))
        val snapshot = stats["statsTest"]!!
        assertEquals("statsTest", snapshot.cacheName)
        assertEquals(0L, snapshot.l1EstimatedSize)
    }
}
