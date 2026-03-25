package com.example.demo.validation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SchemaViolationStoreTest {

    private lateinit var store: SchemaViolationStore

    @BeforeEach
    fun setUp() {
        store = SchemaViolationStore()
    }

    private fun violation(schema: String = "schemas/test.json", endpoint: String = "/api/test", method: String = "POST") =
        SchemaViolation(
            id = UUID.randomUUID().toString(),
            schemaPath = schema,
            endpoint = endpoint,
            method = method,
            violations = listOf("$.field: required property is missing"),
            requestPayload = "{}",
            occurredAt = LocalDateTime.now()
        )

    @Test
    fun `record stores violation and getRecent returns it`() {
        val v = violation()
        store.record(v)

        val recent = store.getRecent(10)
        assertEquals(1, recent.size)
        assertEquals(v.id, recent[0].id)
    }

    @Test
    fun `getRecent returns most recent first`() {
        val v1 = violation(endpoint = "/api/first")
        val v2 = violation(endpoint = "/api/second")
        store.record(v1)
        store.record(v2)

        val recent = store.getRecent(10)
        assertEquals("/api/second", recent[0].endpoint)
        assertEquals("/api/first", recent[1].endpoint)
    }

    @Test
    fun `getRecent respects limit`() {
        repeat(10) { store.record(violation()) }
        val recent = store.getRecent(3)
        assertEquals(3, recent.size)
    }

    @Test
    fun `clear removes all violations`() {
        store.record(violation())
        store.record(violation())
        store.clear()

        assertEquals(0, store.getRecent(100).size)
    }

    @Test
    fun `getStats returns correct total`() {
        store.record(violation(schema = "schemas/a.json"))
        store.record(violation(schema = "schemas/a.json"))
        store.record(violation(schema = "schemas/b.json"))

        val stats = store.getStats()
        assertEquals(3L, stats.total)
        assertEquals(2L, stats.bySchema["schemas/a.json"])
        assertEquals(1L, stats.bySchema["schemas/b.json"])
    }

    @Test
    fun `getStats groups by endpoint and method`() {
        store.record(violation(endpoint = "/api/test", method = "POST"))
        store.record(violation(endpoint = "/api/test", method = "POST"))
        store.record(violation(endpoint = "/api/other", method = "PUT"))

        val stats = store.getStats()
        assertEquals(2L, stats.byEndpoint["POST /api/test"])
        assertEquals(1L, stats.byEndpoint["PUT /api/other"])
    }

    @Test
    fun `store caps at maxSize and evicts oldest`() {
        // Fill beyond 500
        repeat(505) { i -> store.record(violation(endpoint = "/api/$i")) }
        val recent = store.getRecent(1000)
        assertTrue(recent.size <= 500)
    }
}
