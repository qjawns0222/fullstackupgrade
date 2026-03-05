package com.example.demo.interceptor

import com.example.demo.annotation.RateLimit
import com.example.demo.exception.RateLimitExceededException
import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class RateLimitInterceptor(private val proxyManager: ProxyManager<ByteArray>) : HandlerInterceptor {

    override fun preHandle(
            request: HttpServletRequest,
            response: HttpServletResponse,
            handler: Any
    ): Boolean {
        if (handler !is HandlerMethod) return true

        val rateLimit = handler.getMethodAnnotation(RateLimit::class.java) ?: return true

        val key = resolveKey(request, rateLimit)
        val configuration =
                BucketConfiguration.builder()
                        .addLimit {
                            it.capacity(rateLimit.capacity)
                                    .refillGreedy(
                                            rateLimit.tokens,
                                            Duration.ofSeconds(rateLimit.seconds)
                                    )
                        }
                        .build()

        // Use direct configuration build to avoid Kotlin overload ambiguity with Supplier
        val bucket: Bucket = proxyManager.builder().build(key.toByteArray(), configuration)

        if (bucket.tryConsume(1)) {
            return true
        } else {
            throw RateLimitExceededException(
                    "Too many requests for $key. Please try again in a few moments."
            )
        }
    }

    private fun resolveKey(request: HttpServletRequest, rateLimit: RateLimit): String {
        val baseKey = if (rateLimit.key.isNotBlank()) rateLimit.key else request.requestURI
        val clientIp = request.getHeader("X-Forwarded-For") ?: request.remoteAddr
        return "rate-limit:$baseKey:$clientIp"
    }
}
