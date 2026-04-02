package com.example.demo.query

import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener

/**
 * datasource-proxy listener that:
 *  1. Measures elapsed time and raises SlowQueryEvent when threshold exceeded.
 *  2. Counts identical normalized SQLs per-thread and raises N1QueryEvent when threshold exceeded.
 *  3. Triggers EXPLAIN analysis via SlowQueryExplainService for SELECT slow queries.
 */
class SlowQueryListener(
    private val slowQueryThresholdMs: Long,
    private val n1ThresholdCount: Int,
    private val inspector: QueryInspector,
    private val explainService: SlowQueryExplainService? = null
) : QueryExecutionListener {

    override fun beforeQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
        // nothing needed before
    }

    override fun afterQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
        val elapsedMs = execInfo.elapsedTime

        queryInfoList.forEach { queryInfo ->
            val sql = queryInfo.query ?: return@forEach
            val normalized = QueryExecutionContext.normalize(sql)

            // --- Slow Query Check ---
            if (elapsedMs >= slowQueryThresholdMs) {
                val caller = resolveCallerFrame()
                inspector.onSlowQuery(
                    SlowQueryEvent(
                        sql = sql,
                        elapsedTimeMs = elapsedMs,
                        thresholdMs = slowQueryThresholdMs,
                        callerClass = caller.first,
                        callerMethod = caller.second
                    )
                )
                // Trigger EXPLAIN analysis for SELECT queries only
                if (isSelectQuery(sql)) {
                    explainService?.analyzeSlowQuery(sql, elapsedMs)
                }
            }

            // --- N+1 Check ---
            val count = QueryExecutionContext.incrementAndGet(normalized)
            if (count == n1ThresholdCount) {
                val caller = resolveCallerFrame()
                inspector.onN1Detected(
                    N1QueryEvent(
                        normalizedSql = normalized,
                        executionCount = count,
                        thresholdCount = n1ThresholdCount,
                        callerClass = caller.first,
                        callerMethod = caller.second
                    )
                )
            }
        }
    }

    /**
     * Returns true if the SQL starts with SELECT (after trimming whitespace/comments).
     */
    private fun isSelectQuery(sql: String): Boolean =
        sql.trimStart().uppercase().startsWith("SELECT")

    /**
     * Walks the stack to find the first frame inside com.example.demo
     * that is not the proxy infrastructure itself.
     */
    private fun resolveCallerFrame(): Pair<String, String> {
        val frames = Thread.currentThread().stackTrace
        val appFrame = frames.firstOrNull { frame ->
            frame.className.startsWith("com.example.demo") &&
                !frame.className.contains("query.Slow") &&
                !frame.className.contains("query.N1")
        }
        return Pair(
            appFrame?.className?.substringAfterLast(".") ?: "Unknown",
            appFrame?.methodName ?: "unknown"
        )
    }
}
