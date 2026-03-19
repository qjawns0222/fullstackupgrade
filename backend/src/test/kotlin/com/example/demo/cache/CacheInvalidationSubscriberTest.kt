package com.example.demo.cache

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.redis.connection.DefaultMessage

/**
 * Unit tests for [CacheInvalidationSubscriber].
 */
class CacheInvalidationSubscriberTest {

    private lateinit var mockCacheManager: TwoLevelCacheManager
    private lateinit var subscriber: CacheInvalidationSubscriber

    @BeforeEach
    fun setUp() {
        mockCacheManager = mock(TwoLevelCacheManager::class.java)
        subscriber = CacheInvalidationSubscriber(mockCacheManager)
    }

    @Test
    fun `onMessage should parse cacheName and key and call invalidateL1`() {
        val message = DefaultMessage("channel".toByteArray(), "dashboard:user:42".toByteArray())
        subscriber.onMessage(message, null)
        verify(mockCacheManager, times(1)).invalidateL1("dashboard", "user:42")
    }

    @Test
    fun `onMessage with wildcard key should propagate wildcard to invalidateL1`() {
        val message = DefaultMessage("channel".toByteArray(), "jobApplications:*".toByteArray())
        subscriber.onMessage(message, null)
        verify(mockCacheManager, times(1)).invalidateL1("jobApplications", "*")
    }

    @Test
    fun `onMessage with malformed message should not throw`() {
        val message = DefaultMessage("channel".toByteArray(), "malformedmessage".toByteArray())
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            subscriber.onMessage(message, null)
        }
        verify(mockCacheManager, never()).invalidateL1(anyString(), anyString())
    }

    @Test
    fun `onMessage when cacheManager throws should not propagate exception`() {
        `when`(mockCacheManager.invalidateL1(anyString(), anyString()))
            .thenThrow(RuntimeException("unexpected"))

        val message = DefaultMessage("channel".toByteArray(), "dashboard:key1".toByteArray())
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            subscriber.onMessage(message, null)
        }
    }
}
