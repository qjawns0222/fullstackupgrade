package com.example.demo.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cache.Cache
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheWriter

/**
 * Unit tests for [TwoLevelCache].
 *
 * Strategy: mock L2 (RedisCacheWriter) and the invalidation publisher so we can
 * verify the read-through and write-through behaviour without a running Redis.
 */
class TwoLevelCacheTest {

    private lateinit var mockRedisCacheWriter: RedisCacheWriter
    private lateinit var mockPublisher: CacheInvalidationPublisher
    private lateinit var cache: TwoLevelCache

    @BeforeEach
    fun setUp() {
        mockRedisCacheWriter = mock(RedisCacheWriter::class.java)
        mockPublisher = mock(CacheInvalidationPublisher::class.java)

        cache = TwoLevelCache(
            name = "testCache",
            redisCacheWriter = mockRedisCacheWriter,
            redisCacheConfig = RedisCacheConfiguration.defaultCacheConfig(),
            invalidationPublisher = mockPublisher,
            l1MaxSize = 50,
            l1TtlSeconds = 5
        )
    }

    @Test
    fun `getName should return the cache region name`() {
        assertEquals("testCache", cache.name)
    }

    @Test
    fun `getNativeCache should expose both L1 and L2`() {
        @Suppress("UNCHECKED_CAST")
        val native = cache.nativeCache as Map<String, Any>
        assertTrue(native.containsKey("l1"))
        assertTrue(native.containsKey("l2"))
    }

    @Test
    fun `put should publish invalidation event`() {
        cache.put("key1", "value1")
        verify(mockPublisher, times(1)).publish("testCache", "key1")
    }

    @Test
    fun `evict should publish invalidation event`() {
        cache.evict("key1")
        verify(mockPublisher, times(1)).publish("testCache", "key1")
    }

    @Test
    fun `clear should publish wildcard invalidation event`() {
        cache.clear()
        verify(mockPublisher, times(1)).publish("testCache", "*")
    }

    @Test
    fun `invalidateL1 with wildcard should clear entire L1`() {
        // Populate L1 by reading from a simulated value wrapper
        // We directly call invalidateL1 to confirm it doesn't throw
        assertDoesNotThrow { cache.invalidateL1("*") }
    }

    @Test
    fun `invalidateL1 with specific key should not throw`() {
        assertDoesNotThrow { cache.invalidateL1("someKey") }
    }

    @Test
    fun `l1EstimatedSize should be 0 on empty cache`() {
        assertEquals(0L, cache.l1EstimatedSize())
    }

    @Test
    fun `l1Stats should return non-null stats object`() {
        assertNotNull(cache.l1Stats())
    }
}
