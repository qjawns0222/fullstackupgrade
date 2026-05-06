package com.example.demo.query

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class QueryHintEntry(
    val normalizedSql: String,
    val hint: String,
    val slowCount: Int,
    val registeredAt: LocalDateTime
)

/**
 * Tracks slow-query hit counts per normalized SQL.
 * When a SQL pattern reaches the threshold, it is promoted to the hint registry
 * and QueryHintInterceptor will prepend the hint comment on every future execution.
 */
class QueryHintRegistry(private val hintThreshold: Int = 3) {

    private val log = LoggerFactory.getLogger(QueryHintRegistry::class.java)

    private val slowCounts: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    private val hints: ConcurrentHashMap<String, QueryHintEntry> = ConcurrentHashMap()

    /**
     * Records a slow-query hit. Returns true the first time the threshold is crossed.
     */
    fun record(normalizedSql: String): Boolean {
        val count = slowCounts.getOrPut(normalizedSql) { AtomicInteger(0) }.incrementAndGet()
        if (count == hintThreshold && !hints.containsKey(normalizedSql)) {
            val hint = buildHint(normalizedSql)
            hints[normalizedSql] = QueryHintEntry(
                normalizedSql = normalizedSql,
                hint = hint,
                slowCount = count,
                registeredAt = LocalDateTime.now()
            )
            log.warn("[QUERY-HINT] Auto-registered hint for SQL pattern ({}x slow): {}", count, normalizedSql.take(120))
            return true
        }
        return false
    }

    fun getHint(normalizedSql: String): String? = hints[normalizedSql]?.hint

    fun isRegistered(normalizedSql: String): Boolean = hints.containsKey(normalizedSql)

    fun allEntries(): List<QueryHintEntry> = hints.values.toList()

    fun remove(normalizedSql: String) {
        hints.remove(normalizedSql)
        slowCounts.remove(normalizedSql)
    }

    fun clear() {
        hints.clear()
        slowCounts.clear()
    }

    fun slowCount(normalizedSql: String): Int = slowCounts[normalizedSql]?.get() ?: 0

    private fun buildHint(normalizedSql: String): String {
        val upper = normalizedSql.uppercase()
        return when {
            upper.contains("ORDER BY") -> "/*+ NO_FILESORT */"
            upper.contains("JOIN") -> "/*+ USE_INDEX_MERGE */"
            else -> "/*+ MAX_EXECUTION_TIME(5000) */"
        }
    }
}
