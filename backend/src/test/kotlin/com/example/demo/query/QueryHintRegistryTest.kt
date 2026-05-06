package com.example.demo.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueryHintRegistryTest {

    private lateinit var registry: QueryHintRegistry

    @BeforeEach
    fun setUp() {
        registry = QueryHintRegistry(hintThreshold = 3)
    }

    @Test
    fun `record returns false before threshold is reached`() {
        val sql = "SELECT * FROM resumes WHERE id = ?"
        assertFalse(registry.record(sql))
        assertFalse(registry.record(sql))
    }

    @Test
    fun `record returns true exactly at threshold`() {
        val sql = "SELECT * FROM resumes WHERE id = ?"
        registry.record(sql)
        registry.record(sql)
        assertTrue(registry.record(sql))
    }

    @Test
    fun `record returns false after threshold already crossed`() {
        val sql = "SELECT * FROM resumes WHERE id = ?"
        repeat(3) { registry.record(sql) }
        assertFalse(registry.record(sql))
    }

    @Test
    fun `hint is available after threshold`() {
        val sql = "SELECT * FROM resumes WHERE id = ?"
        repeat(3) { registry.record(sql) }
        assertNotNull(registry.getHint(sql))
    }

    @Test
    fun `hint is null before threshold`() {
        val sql = "SELECT * FROM resumes WHERE id = ?"
        registry.record(sql)
        assertNull(registry.getHint(sql))
    }

    @Test
    fun `isRegistered returns true after threshold`() {
        val sql = "SELECT * FROM resumes WHERE id = ?"
        repeat(3) { registry.record(sql) }
        assertTrue(registry.isRegistered(sql))
    }

    @Test
    fun `remove clears hint and count`() {
        val sql = "SELECT * FROM users WHERE id = ?"
        repeat(3) { registry.record(sql) }
        registry.remove(sql)
        assertFalse(registry.isRegistered(sql))
        assertEquals(0, registry.slowCount(sql))
    }

    @Test
    fun `clear removes all entries`() {
        repeat(3) { registry.record("SELECT 1") }
        repeat(3) { registry.record("SELECT 2") }
        registry.clear()
        assertTrue(registry.allEntries().isEmpty())
    }

    @Test
    fun `ORDER BY query gets NO_FILESORT hint`() {
        val sql = "SELECT * FROM resumes ORDER BY created_at DESC"
        repeat(3) { registry.record(sql) }
        assertTrue(registry.getHint(sql)!!.contains("NO_FILESORT"))
    }

    @Test
    fun `JOIN query gets USE_INDEX_MERGE hint`() {
        val sql = "SELECT r.* FROM resumes r JOIN users u ON r.user_id = u.id WHERE u.id = ?"
        repeat(3) { registry.record(sql) }
        assertTrue(registry.getHint(sql)!!.contains("USE_INDEX_MERGE"))
    }

    @Test
    fun `plain query gets MAX_EXECUTION_TIME hint`() {
        val sql = "SELECT * FROM resumes WHERE title = ?"
        repeat(3) { registry.record(sql) }
        assertTrue(registry.getHint(sql)!!.contains("MAX_EXECUTION_TIME"))
    }

    @Test
    fun `different SQL patterns are tracked independently`() {
        val sql1 = "SELECT * FROM resumes WHERE id = ?"
        val sql2 = "SELECT * FROM users WHERE email = ?"
        repeat(3) { registry.record(sql1) }
        registry.record(sql2)
        assertTrue(registry.isRegistered(sql1))
        assertFalse(registry.isRegistered(sql2))
    }

    @Test
    fun `allEntries returns all registered hints`() {
        repeat(3) { registry.record("SELECT * FROM resumes WHERE id = ?") }
        repeat(3) { registry.record("SELECT * FROM users WHERE id = ?") }
        assertEquals(2, registry.allEntries().size)
    }
}
