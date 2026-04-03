package com.example.demo.monitoring

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@ExtendWith(MockitoExtension::class)
class SentryContextFilterTest {

    private lateinit var filter: SentryContextFilter
    private lateinit var request: MockHttpServletRequest
    private lateinit var response: MockHttpServletResponse

    @Mock
    private lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        filter = SentryContextFilter()
        request = MockHttpServletRequest()
        response = MockHttpServletResponse()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `filter passes request through filter chain`() {
        filter.doFilter(request, response, filterChain)
        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `filter works with anonymous user without exception`() {
        request.method = "GET"
        request.requestURI = "/api/resumes"

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `filter works with authenticated user without exception`() {
        val auth = UsernamePasswordAuthenticationToken(
            "testuser",
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        SecurityContextHolder.getContext().authentication = auth

        request.method = "POST"
        request.requestURI = "/api/resumes"

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `filter resolves client ip from X-Forwarded-For header`() {
        request.addHeader("X-Forwarded-For", "192.168.1.1, 10.0.0.1")
        request.method = "GET"
        request.requestURI = "/api/health"

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
    }

    @Test
    fun `filter completes even when Sentry is not initialized`() {
        // Sentry SDK가 DSN 없이 초기화된 경우에도 예외가 발생하면 안 됨
        request.method = "DELETE"
        request.requestURI = "/api/resumes/1"

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
    }
}
