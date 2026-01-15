package com.example.demo.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(private val jwtTokenProvider: JwtTokenProvider) :
        OncePerRequestFilter() {

    override fun doFilterInternal(
            request: HttpServletRequest,
            response: HttpServletResponse,
            filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        val requestUri = request.requestURI

        if (token != null) {
            if (jwtTokenProvider.validateToken(token)) {
                val authentication = jwtTokenProvider.getAuthentication(token)
                SecurityContextHolder.getContext().authentication = authentication
                // println("Security Context set for user: ${authentication.name} at $requestUri")
            } else {
                println("Invalid Token at $requestUri: $token")
            }
        } else {
            // println("No Token found at $requestUri")
        }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }

        // SSE connection (EventSource) usually passes token via query param
        val queryToken = request.getParameter("token")
        if (StringUtils.hasText(queryToken)) {
            return queryToken
        }

        return null
    }
}
