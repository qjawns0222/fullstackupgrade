package com.example.demo.query

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueryExecutionContextTest {

    @AfterEach
    fun cleanup() {
        QueryExecutionContext.clear()
    }

    @Test
    fun `incrementAndGet returns 1 for first execution`() {
        val sql = "SELECT * FROM users WHERE id = 1"
        val normalized = QueryExecutionContext.normalize(sql)
        val count = QueryExecutionContext.incrementAndGet(normalized)
        assertEquals(1, count)
    }

    @Test
    fun `incrementAndGet tracks repeated identical normalized queries`() {
        val sql1 = "SELECT * FROM users WHERE id = 1"
        val sql2 = "SELECT * FROM users WHERE id = 2"
        val normalized1 = QueryExecutionContext.normalize(sql1)
        val normalized2 = QueryExecutionContext.normalize(sql2)
        // Both should normalize to the same key
        assertEquals(normalized1, normalized2)

        QueryExecutionContext.incrementAndGet(normalized1)
        QueryExecutionContext.incrementAndGet(normalized2)
        val count = QueryExecutionContext.incrementAndGet(normalized1)
        assertEquals(3, count)
    }

    @Test
    fun `clear resets counts for next request`() {
        val normalized = QueryExecutionContext.normalize("SELECT 1")
        QueryExecutionContext.incrementAndGet(normalized)
        QueryExecutionContext.incrementAndGet(normalized)
        QueryExecutionContext.clear()
        val countAfterClear = QueryExecutionContext.incrementAndGet(normalized)
        assertEquals(1, countAfterClear)
    }

    @Test
    fun `normalize strips literal numbers`() {
        val sql = "SELECT * FROM orders WHERE user_id = 42 AND status = 1"
        val normalized = QueryExecutionContext.normalize(sql)
        assertEquals("SELECT * FROM orders WHERE user_id = ? AND status = ?", normalized)
    }

    @Test
    fun `normalize strips quoted string literals`() {
        val sql = "SELECT * FROM users WHERE name = 'Alice'"
        val normalized = QueryExecutionContext.normalize(sql)
        assertEquals("SELECT * FROM users WHERE name = ?", normalized)
    }

    @Test
    fun `normalize collapses whitespace`() {
        val sql = "SELECT  *   FROM   users"
        val normalized = QueryExecutionContext.normalize(sql)
        assertEquals("SELECT * FROM users", normalized)
    }
}
