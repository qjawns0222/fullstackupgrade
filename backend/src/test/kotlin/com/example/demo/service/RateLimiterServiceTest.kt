package com.example.demo.service

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder
import java.util.function.Supplier
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class RateLimiterServiceTest {

    @Mock private lateinit var proxyManager: ProxyManager<ByteArray>

    @Mock private lateinit var bucketBuilder: RemoteBucketBuilder<ByteArray>

    @Mock private lateinit var bucket: io.github.bucket4j.distributed.BucketProxy

    private lateinit var rateLimiterService: RateLimiterService

    @BeforeEach
    fun setUp() {
        rateLimiterService = RateLimiterService(proxyManager)
    }

    @Test
    fun `should resolve bucket and allow consumption`() {
        // Given
        val key = "127.0.0.1"

        `when`(proxyManager.builder()).thenReturn(bucketBuilder)
        // We use any() to mock the build(key, supplier) call
        `when`(bucketBuilder.build(any(), any<Supplier<BucketConfiguration>>())).thenReturn(bucket)

        `when`(bucket.tryConsume(1)).thenReturn(true)

        // When
        val resolvedBucket = rateLimiterService.resolveBucket(key)

        // Then
        assertTrue(resolvedBucket.tryConsume(1), "Request should be allowed")
    }
}
