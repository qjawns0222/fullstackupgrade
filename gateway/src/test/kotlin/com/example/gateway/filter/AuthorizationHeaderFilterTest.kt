package com.example.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.nio.charset.StandardCharsets
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class AuthorizationHeaderFilterTest {

    private lateinit var filter: AuthorizationHeaderFilter
    private val secretKey = "mySecretKeyMySecretKeyMySecretKeyMySecretKey" // 32 bytes+

    @BeforeEach
    fun setup() {
        filter = AuthorizationHeaderFilter(secretKey)
    }

    @Test
    @DisplayName("No Authorization Header or Token Param -> 401 Unauthorized")
    fun testNoHeaderOrParam() {
        val config = AuthorizationHeaderFilter.Config()
        val gatewayFilter = filter.apply(config)

        val request = MockServerHttpRequest.get("/").build()
        val exchange = MockServerWebExchange.from(request)
        val chain = mockk<GatewayFilterChain>()

        every { chain.filter(any()) } returns Mono.empty()

        val result = gatewayFilter.filter(exchange, chain)

        StepVerifier.create(result).verifyComplete()
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    @DisplayName("Invalid Token -> 401 Unauthorized")
    fun testInvalidToken() {
        val config = AuthorizationHeaderFilter.Config()
        val gatewayFilter = filter.apply(config)

        val request =
                MockServerHttpRequest.get("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid_token")
                        .build()
        val exchange = MockServerWebExchange.from(request)
        val chain = mockk<GatewayFilterChain>()

        every { chain.filter(any()) } returns Mono.empty()

        val result = gatewayFilter.filter(exchange, chain)

        StepVerifier.create(result).verifyComplete()
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    @DisplayName("Valid Token -> 200 OK & X-User-Id Header")
    fun testValidToken() {
        // Generate valid token
        val key = Keys.hmacShaKeyFor(secretKey.toByteArray(StandardCharsets.UTF_8))
        val token =
                Jwts.builder()
                        .setSubject("test-user-id")
                        .setExpiration(Date(System.currentTimeMillis() + 1000 * 60))
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact()

        val config = AuthorizationHeaderFilter.Config()
        val gatewayFilter = filter.apply(config)

        val request =
                MockServerHttpRequest.get("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .build()
        val exchange = MockServerWebExchange.from(request)
        val chain = mockk<GatewayFilterChain>()

        // Capture the mutated exchange
        val exchangeSlot = io.mockk.slot<ServerWebExchange>()
        every { chain.filter(capture(exchangeSlot)) } returns Mono.empty()

        val result = gatewayFilter.filter(exchange, chain)

        StepVerifier.create(result).verifyComplete()

        // Verify chain was called
        verify(exactly = 1) { chain.filter(any()) }

        // Verify X-User-Id header
        val mutatedRequest = exchangeSlot.captured.request
        assertEquals("test-user-id", mutatedRequest.headers.getFirst("X-User-Id"))
        assertEquals(HttpStatus.OK, (exchange.response.statusCode ?: HttpStatus.OK))
    }
}
