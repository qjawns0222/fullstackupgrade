package com.example.demo.query

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "query.monitor")
data class QueryMonitorProperties(
    @DefaultValue("true")
    val enabled: Boolean = true,
    @DefaultValue("300")
    val slowQueryThresholdMs: Long = 300L,
    @DefaultValue("5")
    val n1ThresholdCount: Int = 5,
    @DefaultValue("3")
    val hintThreshold: Int = 3
)
