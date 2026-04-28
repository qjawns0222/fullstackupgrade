package com.example.demo.cache

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cache.CacheManager
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class CacheWarmupService(
    private val cacheManager: CacheManager,
    private val warmupResumeStore: WarmupResumeStore,
    private val warmupTrendStore: WarmupTrendStore,
) {
    private val log = LoggerFactory.getLogger(CacheWarmupService::class.java)

    @Volatile
    private var lastResult: WarmupResult = WarmupResult(status = WarmupStatus.IDLE)

    @EventListener(ApplicationReadyEvent::class)
    fun warmOnStartup() {
        log.info("Cache warmup triggered by ApplicationReadyEvent")
        runWarmup(progressListener = null)
    }

    fun runWarmup(progressListener: ((WarmupProgress) -> Unit)?): WarmupResult {
        lastResult = WarmupResult(status = WarmupStatus.RUNNING)
        val steps = mutableListOf<WarmupStepResult>()

        steps += warmTrendStats(progressListener)
        steps += warmResumeList(progressListener)

        val result = WarmupResult(
            status = WarmupStatus.DONE,
            steps = steps,
            totalLoaded = steps.sumOf { it.loaded },
        )
        lastResult = result
        log.info("Cache warmup complete: {} entries loaded", result.totalLoaded)
        return result
    }

    fun lastResult(): WarmupResult = lastResult

    private fun warmTrendStats(listener: ((WarmupProgress) -> Unit)?): WarmupStepResult {
        val cacheName = "trendStats"
        return try {
            listener?.invoke(WarmupProgress(cacheName, "시작"))
            val stats = warmupTrendStore.findTop12()
            val cache = cacheManager.getCache(cacheName)
            stats.forEach { ts -> cache?.put(ts.id ?: return@forEach, ts) }
            listener?.invoke(WarmupProgress(cacheName, "완료: ${stats.size}건"))
            log.info("[warmup] {} → {}건 로드", cacheName, stats.size)
            WarmupStepResult(cacheName, stats.size, null)
        } catch (e: Exception) {
            log.warn("[warmup] {} 실패: {}", cacheName, e.message)
            listener?.invoke(WarmupProgress(cacheName, "오류: ${e.message}"))
            WarmupStepResult(cacheName, 0, e.message)
        }
    }

    private fun warmResumeList(listener: ((WarmupProgress) -> Unit)?): WarmupStepResult {
        val cacheName = "resumeList"
        return try {
            listener?.invoke(WarmupProgress(cacheName, "시작"))
            val count = warmupResumeStore.countAll()
            val cache = cacheManager.getCache(cacheName)
            cache?.put("count", count)
            listener?.invoke(WarmupProgress(cacheName, "완료: count=${count}"))
            log.info("[warmup] {} → count={} 로드", cacheName, count)
            WarmupStepResult(cacheName, 1, null)
        } catch (e: Exception) {
            log.warn("[warmup] {} 실패: {}", cacheName, e.message)
            listener?.invoke(WarmupProgress(cacheName, "오류: ${e.message}"))
            WarmupStepResult(cacheName, 0, e.message)
        }
    }
}

enum class WarmupStatus { IDLE, RUNNING, DONE }

data class WarmupProgress(val cacheName: String, val message: String)

data class WarmupStepResult(
    val cacheName: String,
    val loaded: Int,
    val error: String?,
)

data class WarmupResult(
    val status: WarmupStatus,
    val steps: List<WarmupStepResult> = emptyList(),
    val totalLoaded: Int = 0,
)
