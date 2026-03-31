package com.example.demo.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import jakarta.servlet.http.HttpServletResponse

/**
 * Unit tests for [JwtAuthenticationFilter].
 *
 * Uses explicit Mockito.mock() calls instead of @Mock annotations to avoid
 * cross-test contamination from other test classes that leave dangling
 * Mockito argument matchers on the static stack.
 *
 * Uses MockHttpServletRequest/Response to call the public doFilter() method
 * rather than the protected doFilterInternal().
 */
class JwtAuthenticationFilterTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var tokenBlacklistService: TokenBlacklistService
    private lateinit var filterChain: FilterChain
    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        jwtTokenProvider = mock(JwtTokenProvider::class.java)
        tokenBlacklistService = mock(TokenBlacklistService::class.java)
        filterChain = mock(FilterChain::class.java)
        filter = JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistService)
    }

    @Test
    fun `rejects blacklisted token with 401 and stops filter chain`() {
        val blacklistedToken = "blacklisted.jwt.token"
        val request = MockHttpServletRequest("GET", "/api/resumes")
        request.addHeader("Authorization", "Bearer $blacklistedToken")
        val response = MockHttpServletResponse()

        `when`(tokenBlacklistService.isBlacklisted(blacklistedToken)).thenReturn(true)

        filter.doFilter(request, response, filterChain)

        assert(response.status == HttpServletResponse.SC_UNAUTHORIZED) {
            "Expected 401 but got ${response.status}"
        }
        verify(filterChain, never()).doFilter(request, response)
        assert(SecurityContextHolder.getContext().authentication == null)
    }

    @Test
    fun `passes valid non-blacklisted token through`() {
        val validToken = "valid.non.blacklisted.token"
        val mockAuth = mock(org.springframework.security.core.Authentication::class.java)
        val request = MockHttpServletRequest("GET", "/api/resumes")
        request.addHeader("Authorization", "Bearer $validToken")
        val response = MockHttpServletResponse()

        `when`(tokenBlacklistService.isBlacklisted(validToken)).thenReturn(false)
        `when`(jwtTokenProvider.validateToken(validToken)).thenReturn(true)
        `when`(jwtTokenProvider.getAuthentication(validToken)).thenReturn(mockAuth)

        filter.doFilter(request, response, filterChain)

        verify(filterChain, times(1)).doFilter(request, response)
        assert(response.status == 200) { "Expected 200 but got ${response.status}" }
    }

    @Test
    fun `continues chain when no Authorization header`() {
        val request = MockHttpServletRequest("GET", "/api/resumes")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain, times(1)).doFilter(request, response)
        verify(tokenBlacklistService, never()).isBlacklisted(org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun `continues chain for invalid non-blacklisted token`() {
        val tamperedToken = "tampered.jwt.token"
        val request = MockHttpServletRequest("GET", "/api/resumes")
        request.addHeader("Authorization", "Bearer $tamperedToken")
        val response = MockHttpServletResponse()

        `when`(tokenBlacklistService.isBlacklisted(tamperedToken)).thenReturn(false)
        `when`(jwtTokenProvider.validateToken(tamperedToken)).thenReturn(false)

        filter.doFilter(request, response, filterChain)

        verify(filterChain, times(1)).doFilter(request, response)
    }
}
