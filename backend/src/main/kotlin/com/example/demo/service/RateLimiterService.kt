package com.example.demo.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service

@Service
class RateLimiterService {

    private val cache: ConcurrentHashMap<String, Bucket> = ConcurrentHashMap()

    fun resolveBucket(key: String): Bucket {
        return cache.computeIfAbsent(key) { _ -> newBucket() }
    }

    private fun newBucket(): Bucket {
        // 20 requests per minute
        val limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)))
        return Bucket.builder().addLimit(limit).build()
    }
}
