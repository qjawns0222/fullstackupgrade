package com.example.demo.query

/**
 * Port (interface) for query inspection concerns.
 * Implementations decide how to react when slow queries or N+1 patterns are detected.
 */
interface QueryInspector {

    /**
     * Called when a SQL query exceeds the configured slow-query threshold.
     */
    fun onSlowQuery(event: SlowQueryEvent)

    /**
     * Called when the same parameterized SQL is executed more than the N+1 threshold
     * within a single request context.
     */
    fun onN1Detected(event: N1QueryEvent)
}
