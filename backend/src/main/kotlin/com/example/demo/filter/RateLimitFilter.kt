package com.example.demo.filter

import com.example.demo.service.RateLimiterService
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RateLimitFilter(private val rateLimiterService: RateLimiterService) : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        if (httpRequest.requestURI.startsWith("/api")) {
            val ip = getClientIP(httpRequest)
            val bucket = rateLimiterService.resolveBucket(ip)

            val probe = bucket.tryConsumeAndReturnRemaining(1)

            if (probe.isConsumed) {
                httpResponse.addHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
                chain.doFilter(request, response)
            } else {
                httpResponse.status = 429
                httpResponse.addHeader(
                        "X-Rate-Limit-Retry-After-Seconds",
                        probe.nanosToWaitForRefill.toString()
                )
                httpResponse.writer.write("Too many requests. Please try again later.")
                // Stop chain
                return
            }
        } else {
            chain.doFilter(request, response)
        }
    }

    private fun getClientIP(request: HttpServletRequest): String {
        val xfHeader = request.getHeader("X-Forwarded-For")
        if (xfHeader == null) {
            return request.remoteAddr
        }
        return xfHeader.split(",")[0]
    }
}
