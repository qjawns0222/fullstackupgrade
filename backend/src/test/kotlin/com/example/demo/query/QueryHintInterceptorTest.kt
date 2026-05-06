package com.example.demo.query

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueryHintInterceptorTest {

    private lateinit var registry: QueryHintRegistry
    private lateinit var interceptor: QueryHintInterceptor

    @BeforeEach
    fun setUp() {
        registry = QueryHintRegistry(hintThreshold = 2)
        interceptor = QueryHintInterceptor(registry)
    }

    @Test
    fun `returns original sql when no hint registered`() {
        val sql = "SELECT * FROM resumes WHERE id = 1"
        assertEquals(sql, interceptor.inspect(sql))
    }

    @Test
    fun `prepends hint when sql pattern is registered`() {
        val sql = "SELECT * FROM resumes WHERE id = 1"
        val normalized = QueryExecutionContext.normalize(sql)
        repeat(2) { registry.record(normalized) }

        val result = interceptor.inspect(sql)
        assertTrue(result.startsWith("/*+"), "Expected hint prefix, got: $result")
        assertTrue(result.contains(sql), "Expected original SQL in result")
    }

    @Test
    fun `normalizes sql before hint lookup`() {
        // Two different parameterized forms of the same pattern
        val sql1 = "SELECT * FROM resumes WHERE id = 1"
        val sql2 = "SELECT * FROM resumes WHERE id = 42"
        val normalized = QueryExecutionContext.normalize(sql1)
        repeat(2) { registry.record(normalized) }

        // Both should get the hint since they normalize to the same pattern
        val result1 = interceptor.inspect(sql1)
        val result2 = interceptor.inspect(sql2)
        assertTrue(result1.startsWith("/*+"), "sql1 should have hint")
        assertTrue(result2.startsWith("/*+"), "sql2 should have hint")
    }
}
