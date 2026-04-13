package com.example.demo.tenant

import org.springframework.stereotype.Component

@Component
class JpaTenantStore(
    private val repo: TenantRepository
) : TenantStore {

    override fun save(tenant: Tenant) = repo.save(tenant)
    override fun findByTenantId(tenantId: String) = repo.findByTenantId(tenantId)
    override fun findAll() = repo.findAllByOrderByCreatedAtDesc()
    override fun findByStatus(status: TenantStatus) = repo.findByStatus(status)
    override fun existsByTenantId(tenantId: String) = repo.existsByTenantId(tenantId)
    override fun existsBySchemaName(schemaName: String) = repo.existsBySchemaName(schemaName)
}
