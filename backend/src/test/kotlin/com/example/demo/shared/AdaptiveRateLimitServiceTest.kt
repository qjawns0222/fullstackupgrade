package com.example.demo.shared

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AdaptiveRateLimitServiceTest {

    @Mock private lateinit var proxyManager: ProxyManager<ByteArray>
    @Mock private lateinit var bucketBuilder: RemoteBucketBuilder<ByteArray>
    @Mock private lateinit var bucketProxy: io.github.bucket4j.distributed.BucketProxy

    private lateinit var service: AdaptiveRateLimitService

    @BeforeEach
    fun setUp() {
        service = AdaptiveRateLimitService(proxyManager)
    }

    private fun createCircuitBreaker(name: String): CircuitBreaker =
        CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker(name)

    @Test
    fun `기본 정책은 Closed(20 req-min)이다`() {
        val policy = service.currentPolicyFor("unknown-cb")
        assertEquals(AdaptiveRateLimitPolicy.Closed, policy)
        assertEquals(20L, policy.capacity)
    }

    @Test
    fun `CB가 OPEN으로 전환되면 Open 정책(1 req-min)이 적용된다`() {
        val cb = createCircuitBreaker("testCb")
        service.registerCircuitBreaker(cb)

        cb.transitionToOpenState()

        val policy = service.currentPolicyFor("testCb")
        assertEquals(AdaptiveRateLimitPolicy.Open, policy)
        assertEquals(1L, policy.capacity)
    }

    @Test
    fun `CB가 HALF_OPEN으로 전환되면 HalfOpen 정책(5 req-min)이 적용된다`() {
        val cb = createCircuitBreaker("testCb2")
        service.registerCircuitBreaker(cb)

        cb.transitionToOpenState()
        cb.transitionToHalfOpenState()

        val policy = service.currentPolicyFor("testCb2")
        assertEquals(AdaptiveRateLimitPolicy.HalfOpen, policy)
        assertEquals(5L, policy.capacity)
    }

    @Test
    fun `CB가 CLOSED로 복구되면 Closed 정책(20 req-min)이 복원된다`() {
        val cb = createCircuitBreaker("testCb3")
        service.registerCircuitBreaker(cb)

        cb.transitionToOpenState()
        cb.transitionToHalfOpenState()
        cb.transitionToClosedState()

        val policy = service.currentPolicyFor("testCb3")
        assertEquals(AdaptiveRateLimitPolicy.Closed, policy)
        assertEquals(20L, policy.capacity)
    }

    @Test
    fun `snapshot은 등록된 CB의 정책 정보를 반환한다`() {
        val cb = createCircuitBreaker("snapCb")
        service.registerCircuitBreaker(cb)
        cb.transitionToOpenState()

        val snap = service.snapshot()
        assertEquals(1, snap.size)
        assertEquals("Open", snap["snapCb"]?.policyName)
        assertEquals(1L, snap["snapCb"]?.capacityPerMinute)
    }

    @Test
    fun `resolveBucket은 현재 정책으로 Bucket을 반환한다`() {
        `when`(proxyManager.builder()).thenReturn(bucketBuilder)
        `when`(bucketBuilder.build(any(), any(BucketConfiguration::class.java))).thenReturn(bucketProxy)

        val cb = createCircuitBreaker("bucketCb")
        service.registerCircuitBreaker(cb)

        val bucket = service.resolveBucket("bucketCb", "127.0.0.1")
        assertEquals(bucketProxy, bucket)
    }
}
