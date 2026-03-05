package com.example.demo.interceptor

import com.example.demo.annotation.RateLimit
import com.example.demo.exception.RateLimitExceededException
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.springframework.web.method.HandlerMethod

class RateLimitInterceptorTest {

    private lateinit var proxyManager: ProxyManager<ByteArray>
    private lateinit var interceptor: RateLimitInterceptor
    private lateinit var request: HttpServletRequest
    private lateinit var response: HttpServletResponse
    private lateinit var handlerMethod: HandlerMethod
    private lateinit var rateLimit: RateLimit

    @BeforeEach
    fun setUp() {
        proxyManager = mock(ProxyManager::class.java) as ProxyManager<ByteArray>
        interceptor = RateLimitInterceptor(proxyManager)
        request = mock(HttpServletRequest::class.java)
        response = mock(HttpServletResponse::class.java)
        handlerMethod = mock(HandlerMethod::class.java)
        rateLimit = mock(RateLimit::class.java)
    }

    @Test
    fun `should return true when token is consumed`() {
        // Given
        `when`(handlerMethod.getMethodAnnotation(RateLimit::class.java)).thenReturn(rateLimit)
        `when`(rateLimit.key).thenReturn("test")
        `when`(rateLimit.capacity).thenReturn(10)
        `when`(rateLimit.tokens).thenReturn(10)
        `when`(rateLimit.seconds).thenReturn(60)
        `when`(request.requestURI).thenReturn("/test")
        `when`(request.remoteAddr).thenReturn("127.0.0.1")

        val builder = mock(RemoteBucketBuilder::class.java) as RemoteBucketBuilder<ByteArray>
        val bucket = mock(BucketProxy::class.java)

        `when`(proxyManager.builder()).thenReturn(builder)

        doReturn(bucket)
                .`when`(builder)
                .build(
                        ArgumentMatchers.any(ByteArray::class.java),
                        ArgumentMatchers.any(BucketConfiguration::class.java)
                )

        `when`(bucket.tryConsume(1)).thenReturn(true)

        // When
        val result = interceptor.preHandle(request, response, handlerMethod)

        // Then
        assertTrue(result)
    }

    @Test
    fun `should throw RateLimitExceededException when tokens exhausted`() {
        // Given
        `when`(handlerMethod.getMethodAnnotation(RateLimit::class.java)).thenReturn(rateLimit)
        `when`(rateLimit.key).thenReturn("test")
        `when`(rateLimit.capacity).thenReturn(10)
        `when`(rateLimit.tokens).thenReturn(10)
        `when`(rateLimit.seconds).thenReturn(60)
        `when`(request.requestURI).thenReturn("/test")
        `when`(request.remoteAddr).thenReturn("127.0.0.1")

        val builder = mock(RemoteBucketBuilder::class.java) as RemoteBucketBuilder<ByteArray>
        val bucket = mock(BucketProxy::class.java)

        `when`(proxyManager.builder()).thenReturn(builder)

        doReturn(bucket)
                .`when`(builder)
                .build(
                        ArgumentMatchers.any(ByteArray::class.java),
                        ArgumentMatchers.any(BucketConfiguration::class.java)
                )

        `when`(bucket.tryConsume(1)).thenReturn(false)

        // When & Then
        assertThrows<RateLimitExceededException> {
            interceptor.preHandle(request, response, handlerMethod)
        }
    }
}
