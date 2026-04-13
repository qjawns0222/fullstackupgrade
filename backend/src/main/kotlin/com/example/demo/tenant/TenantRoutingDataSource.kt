package com.example.demo.tenant

import org.slf4j.LoggerFactory
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource

/**
 * 현재 스레드의 TenantContext를 읽어 라우팅 키로 반환한다.
 * DataSource 맵에 tenantId 키가 없으면 null을 반환하여 기본 DataSource를 사용한다.
 */
class TenantRoutingDataSource : AbstractRoutingDataSource() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun determineCurrentLookupKey(): Any? {
        val tenantId = TenantContext.get()
        if (tenantId != null) {
            log.debug("Routing to tenant DataSource: {}", tenantId)
        }
        return tenantId
    }
}
