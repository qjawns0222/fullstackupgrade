package com.example.demo.query

data class QueryMonitorSummary(
    val totalSlowQueries: Int,
    val totalN1Detections: Int,
    val recentSlowQueries: List<SlowQueryEvent>,
    val recentN1Alerts: List<N1QueryEvent>
)
