package com.example.demo.filter

import com.example.demo.service.RateLimiterService
import io.github.bucket4j.Bucket
import io.github.bucket4j.ConsumptionProbe
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.PrintWriter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class RateLimitFilterTest {

    @Mock lateinit var rateLimiterService: RateLimiterService

    @InjectMocks lateinit var rateLimitFilter: RateLimitFilter

    @Test
    fun `should return 429 when bucket is empty`() {
        // Given
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)
        val writer = mock(PrintWriter::class.java)

        `when`(request.requestURI).thenReturn("/api/test")
        `when`(request.remoteAddr).thenReturn("127.0.0.1")
        `when`(response.writer).thenReturn(writer)

        val bucket = mock(Bucket::class.java)
        `when`(rateLimiterService.resolveBucket(anyString())).thenReturn(bucket)

        val probe = mock(ConsumptionProbe::class.java)
        `when`(probe.isConsumed).thenReturn(false) // FLAGGED: Consumed = false means Empty
        `when`(probe.nanosToWaitForRefill).thenReturn(10_000_000_000L) // 10s

        `when`(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe)

        // When
        rateLimitFilter.doFilter(request, response, chain)

        // Then
        verify(response).status = 429
        verify(response).addHeader(eq("X-Rate-Limit-Retry-After-Seconds"), anyString())
    }

    @Test
    fun `should proceed when bucket has tokens`() {
        // Given
        val request = mock(HttpServletRequest::class.java)
        val response = mock(HttpServletResponse::class.java)
        val chain = mock(FilterChain::class.java)

        `when`(request.requestURI).thenReturn("/api/test")
        `when`(request.remoteAddr).thenReturn("127.0.0.1")

        val bucket = mock(Bucket::class.java)
        `when`(rateLimiterService.resolveBucket(anyString())).thenReturn(bucket)

        val probe = mock(ConsumptionProbe::class.java)
        `when`(probe.isConsumed).thenReturn(true) // Consumed = true
        `when`(probe.remainingTokens).thenReturn(19)

        `when`(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe)

        // When
        rateLimitFilter.doFilter(request, response, chain)

        // Then
        verify(chain).doFilter(request, response)
        verify(response).addHeader("X-Rate-Limit-Remaining", "19")
    }
}
