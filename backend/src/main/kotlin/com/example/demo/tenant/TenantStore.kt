package com.example.demo.tenant

interface TenantStore {
    fun save(tenant: Tenant): Tenant
    fun findByTenantId(tenantId: String): Tenant?
    fun findAll(): List<Tenant>
    fun findByStatus(status: TenantStatus): List<Tenant>
    fun existsByTenantId(tenantId: String): Boolean
    fun existsBySchemaName(schemaName: String): Boolean
}
