package com.example.demo.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.StringUtils
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenBlacklistService: TokenBlacklistService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = resolveToken(request)
        val requestUri = request.requestURI

        if (token != null) {
            when {
                tokenBlacklistService.isBlacklisted(token) -> {
                    log.warn("Rejected blacklisted JWT at {}", requestUri)
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked")
                    return
                }
                jwtTokenProvider.validateToken(token) -> {
                    val authentication = jwtTokenProvider.getAuthentication(token)
                    SecurityContextHolder.getContext().authentication = authentication
                }
                else -> {
                    log.debug("Invalid Token at {}", requestUri)
                }
            }
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
