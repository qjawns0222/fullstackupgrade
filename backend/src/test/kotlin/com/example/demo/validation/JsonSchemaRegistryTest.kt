package com.example.demo.validation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonSchemaRegistryTest {

    private val registry = JsonSchemaRegistry()

    @Test
    fun `getSchema loads job-application schema from classpath`() {
        val schema = registry.getSchema("schemas/job-application.json")
        assertNotNull(schema)
    }

    @Test
    fun `getSchema caches schema on second call`() {
        val s1 = registry.getSchema("schemas/job-application.json")
        val s2 = registry.getSchema("schemas/job-application.json")
        assertSame(s1, s2)
    }

    @Test
    fun `getSchema throws for missing file`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.getSchema("schemas/nonexistent.json")
        }
        assertTrue(ex.message!!.contains("nonexistent.json"))
    }

    @Test
    fun `getSchema loads login-request schema from classpath`() {
        val schema = registry.getSchema("schemas/login-request.json")
        assertNotNull(schema)
    }
}
