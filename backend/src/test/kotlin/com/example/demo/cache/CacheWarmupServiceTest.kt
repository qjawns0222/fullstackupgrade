package com.example.demo.cache

import com.example.demo.entity.TrendStats
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheFactoryBean
import org.springframework.cache.concurrent.ConcurrentMapCache

class CacheWarmupServiceTest {

    private lateinit var resumeStore: FakeWarmupResumeStore
    private lateinit var trendStore: FakeWarmupTrendStore
    private lateinit var cacheManager: SimpleCacheManager
    private lateinit var service: CacheWarmupService

    @BeforeEach
    fun setUp() {
        resumeStore = FakeWarmupResumeStore()
        trendStore = FakeWarmupTrendStore()
        cacheManager = SimpleCacheManager().apply {
            setCaches(listOf(
                ConcurrentMapCache("resumeList"),
                ConcurrentMapCache("trendStats"),
            ))
            afterPropertiesSet()
        }
        service = CacheWarmupService(cacheManager, resumeStore, trendStore)
    }

    @Test
    fun `runWarmup - returns DONE status`() {
        val result = service.runWarmup(null)
        assertEquals(WarmupStatus.DONE, result.status)
    }

    @Test
    fun `runWarmup - loads trendStats into cache`() {
        trendStore.stats = listOf(
            TrendStats(techStack = "Kotlin", count = 50).apply { id = 1L },
            TrendStats(techStack = "Java", count = 30).apply { id = 2L },
        )

        service.runWarmup(null)

        val cache = cacheManager.getCache("trendStats")!!
        assertNotNull(cache.get(1L))
        assertNotNull(cache.get(2L))
    }

    @Test
    fun `runWarmup - loads resumeList count into cache`() {
        resumeStore.total = 42L

        service.runWarmup(null)

        val cache = cacheManager.getCache("resumeList")!!
        assertEquals(42L, cache.get("count")?.get())
    }

    @Test
    fun `runWarmup - totalLoaded reflects all steps`() {
        trendStore.stats = listOf(
            TrendStats(techStack = "Go", count = 10).apply { id = 1L },
        )
        resumeStore.total = 100L

        val result = service.runWarmup(null)

        // trendStats: 1건 + resumeList: 1건(count key)
        assertEquals(2, result.totalLoaded)
    }

    @Test
    fun `runWarmup - reports progress events`() {
        val events = mutableListOf<WarmupProgress>()
        service.runWarmup { events.add(it) }

        assertTrue(events.any { it.cacheName == "trendStats" })
        assertTrue(events.any { it.cacheName == "resumeList" })
    }

    @Test
    fun `lastResult - returns IDLE before first warmup`() {
        assertEquals(WarmupStatus.IDLE, service.lastResult().status)
    }

    @Test
    fun `lastResult - reflects result after warmup`() {
        service.runWarmup(null)
        assertEquals(WarmupStatus.DONE, service.lastResult().status)
    }

    @Test
    fun `runWarmup - step records error when store throws`() {
        trendStore.shouldThrow = true

        val result = service.runWarmup(null)

        val trendStep = result.steps.find { it.cacheName == "trendStats" }!!
        assertNotNull(trendStep.error)
        assertEquals(0, trendStep.loaded)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

class FakeWarmupResumeStore : WarmupResumeStore {
    var total: Long = 0L
    override fun findAllIds(): List<Long> = emptyList()
    override fun countAll(): Long = total
}

class FakeWarmupTrendStore : WarmupTrendStore {
    var stats: List<TrendStats> = emptyList()
    var shouldThrow: Boolean = false

    override fun findTop12(): List<TrendStats> {
        if (shouldThrow) throw RuntimeException("DB connection failed")
        return stats
    }
}
