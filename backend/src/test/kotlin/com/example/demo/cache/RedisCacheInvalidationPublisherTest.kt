package com.example.demo.cache

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Unit tests for [RedisCacheInvalidationPublisher].
 */
class RedisCacheInvalidationPublisherTest {

    private lateinit var mockRedisTemplate: StringRedisTemplate
    private lateinit var props: TwoLevelCacheProperties
    private lateinit var publisher: RedisCacheInvalidationPublisher

    @BeforeEach
    fun setUp() {
        mockRedisTemplate = mock(StringRedisTemplate::class.java)
        props = TwoLevelCacheProperties(invalidationTopic = "cache:invalidation")
        publisher = RedisCacheInvalidationPublisher(mockRedisTemplate, props)
    }

    @Test
    fun `publish should send formatted message to Redis topic`() {
        publisher.publish("dashboard", "user:42")
        verify(mockRedisTemplate, times(1))
            .convertAndSend("cache:invalidation", "dashboard:user:42")
    }

    @Test
    fun `publish should not throw when Redis is unavailable`() {
        `when`(mockRedisTemplate.convertAndSend(anyString(), anyString()))
            .thenThrow(RuntimeException("Redis connection refused"))

        // Should swallow exception and log a warning instead of propagating
        org.junit.jupiter.api.Assertions.assertDoesNotThrow {
            publisher.publish("dashboard", "someKey")
        }
    }

    @Test
    fun `publish with wildcard key should send correct message`() {
        publisher.publish("jobApplications", "*")
        verify(mockRedisTemplate, times(1))
            .convertAndSend("cache:invalidation", "jobApplications:*")
    }
}
