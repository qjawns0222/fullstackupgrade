package com.example.demo.perf

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

/**
 * Intercepts every HTTP request and feeds duration + status into HttpMetricsStore.
 *
 * Path normalization uses Spring MVC's best-match URI template stored by
 * DispatcherServlet so that /api/resumes/42 and /api/resumes/99 both bucket
 * under "GET /api/resumes/{id}" rather than creating unbounded cardinality.
 *
 * Runs after DispatcherServlet attribute population (Ordered.LOWEST_PRECEDENCE - 5)
 * so the pattern attribute is always present on the way out.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5)
class HttpMetricsFilter(
    private val metricsStore: HttpMetricsStore
) : OncePerRequestFilter() {

    // Static resources and actuator endpoints are noise — skip them
    private val excludedPrefixes = listOf(
        "/actuator", "/graphiql", "/favicon", "/static", "/css", "/js"
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        if (shouldExclude(request.requestURI)) {
            chain.doFilter(request, response)
            return
        }

        val start = System.currentTimeMillis()
        try {
            chain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - start
            val pattern = resolvePattern(request)
            val endpoint = "${request.method} $pattern"
            metricsStore.record(endpoint, duration, response.status)
        }
    }

    private fun resolvePattern(request: HttpServletRequest): String {
        // Spring MVC stores the matched URI template here after routing
        val template = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
        return template?.toString() ?: request.requestURI
    }

    private fun shouldExclude(uri: String): Boolean =
        excludedPrefixes.any { uri.startsWith(it) }
}
