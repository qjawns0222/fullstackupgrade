package com.example.demo.logging

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import java.time.LocalDateTime

/**
 * Fake store — avoids all Mockito argument matcher NPE issues with Kotlin non-null types.
 */
class FakeHttpAccessLogStore : HttpAccessLogStore {

    val savedDocs = mutableListOf<HttpAccessLogDocument>()
    var throwOnSave: Boolean = false
    private var _count: Long = 0L
    var recentPage: Page<HttpAccessLogDocument> = PageImpl(emptyList())
    var statusPage: Page<HttpAccessLogDocument> = PageImpl(emptyList())
    var userPage: Page<HttpAccessLogDocument> = PageImpl(emptyList())

    fun setCount(n: Long) { _count = n }

    override fun save(doc: HttpAccessLogDocument): HttpAccessLogDocument {
        if (throwOnSave) throw RuntimeException("ES unavailable")
        savedDocs.add(doc)
        return doc
    }

    override fun findRecent(page: Int, size: Int): Page<HttpAccessLogDocument> = recentPage
    override fun findByStatus(status: Int, page: Int, size: Int): Page<HttpAccessLogDocument> = statusPage
    override fun findByUserId(userId: String, page: Int, size: Int): Page<HttpAccessLogDocument> = userPage
    override fun count(): Long = _count
}

class HttpAccessLogServiceTest {

    private lateinit var fakeStore: FakeHttpAccessLogStore
    private lateinit var service: HttpAccessLogService

    @BeforeEach
    fun setUp() {
        fakeStore = FakeHttpAccessLogStore()
        service = HttpAccessLogService(fakeStore)
    }

    private fun makeDoc(requestId: String = "req-1", status: Int = 200, userId: String? = null) =
        HttpAccessLogDocument(
            requestId = requestId,
            method = "GET",
            path = "/api/test",
            status = status,
            durationMs = 42L,
            clientIp = "127.0.0.1",
            userId = userId,
            timestamp = LocalDateTime.now()
        )

    @Test
    fun `record should save document with correct fields`() {
        service.record(
            requestId = "req-123",
            method = "POST",
            path = "/api/applications",
            status = 201,
            durationMs = 88L,
            clientIp = "192.168.1.1",
            userId = "user-42"
        )

        assertEquals(1, fakeStore.savedDocs.size)
        val saved = fakeStore.savedDocs[0]
        assertEquals("req-123", saved.requestId)
        assertEquals("POST", saved.method)
        assertEquals("/api/applications", saved.path)
        assertEquals(201, saved.status)
        assertEquals(88L, saved.durationMs)
        assertEquals("192.168.1.1", saved.clientIp)
        assertEquals("user-42", saved.userId)
    }

    @Test
    fun `record should save document even when userId is null`() {
        service.record(
            requestId = "req-anon",
            method = "GET",
            path = "/api/public",
            status = 200,
            durationMs = 15L,
            clientIp = "10.0.0.1",
            userId = null
        )

        assertEquals(1, fakeStore.savedDocs.size)
        assertNull(fakeStore.savedDocs[0].userId)
    }

    @Test
    fun `record should not throw when store throws`() {
        fakeStore.throwOnSave = true

        assertDoesNotThrow {
            service.record("req-fail", "GET", "/api/test", 200, 10L, "127.0.0.1", null)
        }
    }

    @Test
    fun `record should not persist doc when store throws`() {
        fakeStore.throwOnSave = true
        service.record("req-fail", "GET", "/api/test", 200, 10L, "127.0.0.1", null)
        assertEquals(0, fakeStore.savedDocs.size)
    }

    @Test
    fun `getRecentLogs should return page from store`() {
        fakeStore.recentPage = PageImpl(listOf(makeDoc()))

        val result = service.getRecentLogs(0, 20)

        assertEquals(1, result.content.size)
        assertEquals("req-1", result.content[0].requestId)
    }

    @Test
    fun `getLogsByStatus should filter by status`() {
        fakeStore.statusPage = PageImpl(listOf(makeDoc(status = 500)))

        val result = service.getLogsByStatus(500, 0, 10)

        assertEquals(1, result.content.size)
        assertEquals(500, result.content[0].status)
    }

    @Test
    fun `getLogsByUserId should filter by userId`() {
        fakeStore.userPage = PageImpl(listOf(makeDoc(userId = "user-99")))

        val result = service.getLogsByUserId("user-99", 0, 10)

        assertEquals(1, result.content.size)
        assertEquals("user-99", result.content[0].userId)
    }

    @Test
    fun `getLogSummary should return total request count`() {
        fakeStore.setCount(1337L)

        val summary = service.getLogSummary()

        assertEquals(1337L, summary.totalRequests)
    }
}
