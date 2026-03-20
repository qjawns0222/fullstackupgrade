package com.example.demo.query

/**
 * Thread-local holder that tracks per-request SQL execution counts
 * for N+1 pattern detection.
 */
object QueryExecutionContext {

    private val sqlCountMap: ThreadLocal<MutableMap<String, Int>> =
        ThreadLocal.withInitial { mutableMapOf() }

    fun incrementAndGet(normalizedSql: String): Int {
        val map = sqlCountMap.get()
        val newCount = (map[normalizedSql] ?: 0) + 1
        map[normalizedSql] = newCount
        return newCount
    }

    fun clear() {
        sqlCountMap.remove()
    }

    /**
     * Strips literal parameter values from SQL to produce a stable normalized form,
     * so that repeated "SELECT ... WHERE id = ?" queries map to the same key regardless of value.
     */
    fun normalize(sql: String): String =
        sql.replace(Regex("\\b\\d+\\b"), "?")
            .replace(Regex("'[^']*'"), "?")
            .replace(Regex("\\s+"), " ")
            .trim()
}
