package com.example.demo.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.distributed.proxy.ProxyManager
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.springframework.stereotype.Service

@Service
class RateLimiterService(private val proxyManager: ProxyManager<ByteArray>) {

    fun resolveBucket(key: String): Bucket {
        val bytes = key.toByteArray(StandardCharsets.UTF_8)
        return proxyManager.builder().build(bytes) {
            // 20 requests per minute
            val limit =
                    Bandwidth.builder().capacity(20).refillGreedy(20, Duration.ofMinutes(1)).build()
            io.github.bucket4j.BucketConfiguration.builder().addLimit(limit).build()
        }
    }
}
