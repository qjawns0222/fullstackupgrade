package com.example.demo.tenant

import org.springframework.data.jpa.repository.JpaRepository

interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findByTenantId(tenantId: String): Tenant?
    fun findAllByOrderByCreatedAtDesc(): List<Tenant>
    fun findByStatus(status: TenantStatus): List<Tenant>
    fun existsByTenantId(tenantId: String): Boolean
    fun existsBySchemaName(schemaName: String): Boolean
}
