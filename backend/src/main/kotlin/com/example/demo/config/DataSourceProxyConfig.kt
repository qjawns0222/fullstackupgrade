package com.example.demo.config

import com.example.demo.query.QueryInspector
import com.example.demo.query.QueryMonitorProperties
import com.example.demo.query.SlowQueryExplainService
import com.example.demo.query.SlowQueryListener
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Wraps the auto-configured DataSource with datasource-proxy so that
 * every SQL execution passes through SlowQueryListener.
 *
 * Activated only when query.monitor.enabled=true (default).
 */
@Configuration
@EnableConfigurationProperties(QueryMonitorProperties::class)
@ConditionalOnProperty(name = ["query.monitor.enabled"], havingValue = "true", matchIfMissing = true)
class DataSourceProxyConfig {

    @Bean
    @Primary
    fun proxyDataSource(
        @Qualifier("dataSource") original: DataSource,
        properties: QueryMonitorProperties,
        inspector: QueryInspector,
        explainService: SlowQueryExplainService
    ): DataSource {
        val listener = SlowQueryListener(
            slowQueryThresholdMs = properties.slowQueryThresholdMs,
            n1ThresholdCount = properties.n1ThresholdCount,
            inspector = inspector,
            explainService = explainService
        )

        return ProxyDataSourceBuilder
            .create(original)
            .name("ProxyDS")
            .listener(listener)
            .build()
    }
}
