package com.example.demo.logging

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Fake service that records calls without touching Elasticsearch.
 * Avoids Mockito argument-matcher NPE issues with Kotlin non-null types.
 */
class FakeAccessLogService : HttpAccessLogService(FakeHttpAccessLogStore()) {

    data class Call(
        val requestId: String,
        val method: String,
        val path: String,
        val status: Int,
        val durationMs: Long,
        val clientIp: String,
        val userId: String?
    )

    val calls = mutableListOf<Call>()

    override fun record(
        requestId: String,
        method: String,
        path: String,
        status: Int,
        durationMs: Long,
        clientIp: String,
        userId: String?
    ) {
        calls.add(Call(requestId, method, path, status, durationMs, clientIp, userId))
    }
}

@ExtendWith(MockitoExtension::class)
class RequestCorrelationFilterTest {

    @Mock private lateinit var chain: FilterChain

    private lateinit var fakeService: FakeAccessLogService
    private lateinit var filter: RequestCorrelationFilter

    @BeforeEach
    fun setUp() {
        fakeService = FakeAccessLogService()
        filter = RequestCorrelationFilter(fakeService)
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    private fun request(
        method: String = "GET",
        uri: String = "/api/test",
        remoteAddr: String = "127.0.0.1",
        requestId: String? = null,
        forwarded: String? = null
    ): MockHttpServletRequest {
        val req = MockHttpServletRequest(method, uri)
        req.remoteAddr = remoteAddr
        if (requestId != null) req.addHeader("X-Request-Id", requestId)
        if (forwarded != null) req.addHeader("X-Forwarded-For", forwarded)
        return req
    }

    @Test
    fun `should set X-Request-Id header on response when none provided`() {
        val req = request()
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        val header = res.getHeader("X-Request-Id")
        assertNotNull(header)
        assertTrue(header!!.isNotBlank())
    }

    @Test
    fun `should propagate provided X-Request-Id`() {
        val knownId = "test-correlation-id-123"
        val req = request(requestId = knownId, method = "POST", uri = "/api/applications")
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        assertEquals(knownId, res.getHeader("X-Request-Id"))
    }

    @Test
    fun `should clear MDC after request completes`() {
        val req = request()
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        assertNull(MDC.get(MdcKeys.REQUEST_ID))
        assertNull(MDC.get(MdcKeys.HTTP_METHOD))
        assertNull(MDC.get(MdcKeys.HTTP_PATH))
    }

    @Test
    fun `should clear MDC even when filter chain throws exception`() {
        val req = request(method = "DELETE", uri = "/api/resumes/1")
        val res = MockHttpServletResponse()

        doThrow(RuntimeException("downstream failure")).`when`(chain).doFilter(req, res)

        assertThrows(RuntimeException::class.java) {
            filter.doFilter(req, res, chain)
        }

        assertNull(MDC.get(MdcKeys.REQUEST_ID))
    }

    @Test
    fun `should use first IP in X-Forwarded-For chain`() {
        val req = request(forwarded = "203.0.113.5, 10.0.0.1")
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        assertEquals(1, fakeService.calls.size)
        assertEquals("203.0.113.5", fakeService.calls[0].clientIp)
    }

    @Test
    fun `should invoke filter chain exactly once`() {
        val req = request()
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        verify(chain, times(1)).doFilter(req, res)
    }

    @Test
    fun `should call httpAccessLogService record with correct requestId`() {
        val req = request(method = "GET", uri = "/api/dashboard", requestId = "req-abc")
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        assertEquals(1, fakeService.calls.size)
        val call = fakeService.calls[0]
        assertEquals("req-abc", call.requestId)
        assertEquals("GET", call.method)
        assertEquals("/api/dashboard", call.path)
        assertTrue(call.durationMs >= 0)
    }

    @Test
    fun `should use remoteAddr when X-Forwarded-For is absent`() {
        val req = request(remoteAddr = "192.168.0.42")
        val res = MockHttpServletResponse()

        filter.doFilter(req, res, chain)

        assertEquals(1, fakeService.calls.size)
        assertEquals("192.168.0.42", fakeService.calls[0].clientIp)
    }
}
