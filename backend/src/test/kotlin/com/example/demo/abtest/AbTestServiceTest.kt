package com.example.demo.abtest

import io.getunleash.FakeUnleash
import io.getunleash.Variant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AbTestServiceTest {

    private lateinit var fakeUnleash: FakeUnleash
    private lateinit var fakeStore: FakeAbTestStore
    private lateinit var service: AbTestService

    @BeforeEach
    fun setUp() {
        fakeUnleash = FakeUnleash()
        fakeStore = FakeAbTestStore()
        service = AbTestService(fakeUnleash, fakeStore)
    }

    @Test
    fun `getVariant returns disabled variant when toggle is off`() {
        val result = service.getVariant("my-toggle", userId = "user1")

        assertEquals("disabled", result.variantName)
        assertEquals("my-toggle", result.toggleName)
        assertEquals("user1", result.userId)
    }

    @Test
    fun `getVariant records result in store`() {
        fakeUnleash.enable("my-toggle")
        fakeUnleash.setVariant("my-toggle", Variant("B", null as String?, true))

        service.getVariant("my-toggle", userId = "user1", sessionId = "sess-1")

        assertEquals(1, fakeStore.saved.size)
        val saved = fakeStore.saved.first()
        assertEquals("B", saved.variantName)
        assertEquals("user1", saved.userId)
        assertEquals("sess-1", saved.sessionId)
    }

    @Test
    fun `getStats aggregates variant counts`() {
        fakeStore.saved.addAll(listOf(
            AbTestResult(toggleName = "exp", variantName = "A", recordedAt = LocalDateTime.now()),
            AbTestResult(toggleName = "exp", variantName = "A", recordedAt = LocalDateTime.now()),
            AbTestResult(toggleName = "exp", variantName = "B", recordedAt = LocalDateTime.now()),
        ))

        val stats = service.getStats("exp", periodHours = 24)

        assertEquals("exp", stats.toggleName)
        assertEquals(2L, stats.variants["A"])
        assertEquals(1L, stats.variants["B"])
        assertEquals(3L, stats.total)
    }

    @Test
    fun `getRecentResults returns results for toggle`() {
        fakeStore.saved.addAll(listOf(
            AbTestResult(toggleName = "exp", variantName = "A"),
            AbTestResult(toggleName = "other", variantName = "B"),
        ))

        val results = service.getRecentResults("exp", limit = 10)

        assertEquals(1, results.size)
        assertEquals("exp", results.first().toggleName)
    }

    @Test
    fun `recordVariant saves with given data`() {
        val result = service.recordVariant(
            toggleName = "checkout-flow",
            variantName = "new-ui",
            userId = "u42",
            sessionId = "s99",
            payload = """{"color":"blue"}"""
        )

        assertEquals("checkout-flow", result.toggleName)
        assertEquals("new-ui", result.variantName)
        assertEquals("u42", result.userId)
        assertEquals("""{"color":"blue"}""", result.payload)
    }
}

class FakeAbTestStore : AbTestStore {
    val saved = mutableListOf<AbTestResult>()
    private var idSeq = 1L

    override fun save(result: AbTestResult): AbTestResult =
        result.copy(id = idSeq++).also { saved.add(it) }

    override fun countByToggleAndVariantSince(toggleName: String, since: LocalDateTime): Map<String, Long> =
        saved.filter { it.toggleName == toggleName && it.recordedAt >= since }
            .groupingBy { it.variantName }
            .eachCount()
            .mapValues { it.value.toLong() }

    override fun findRecentByToggle(toggleName: String, limit: Int): List<AbTestResult> =
        saved.filter { it.toggleName == toggleName }.takeLast(limit)
}
