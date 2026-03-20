package com.example.demo.query

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Servlet filter that clears the per-request N+1 tracking context
 * after each HTTP request completes, preventing state leakage across requests.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class QueryContextClearingFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        try {
            chain.doFilter(request, response)
        } finally {
            QueryExecutionContext.clear()
        }
    }
}
