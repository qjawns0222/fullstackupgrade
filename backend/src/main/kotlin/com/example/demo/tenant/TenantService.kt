package com.example.demo.tenant

import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TenantService(private val store: TenantStore) {

    fun createTenant(request: CreateTenantRequest): Tenant {
        require(!store.existsByTenantId(request.tenantId)) {
            "Tenant already exists: ${request.tenantId}"
        }
        val schemaName = "tenant_${request.tenantId.lowercase().replace("-", "_")}"
        require(!store.existsBySchemaName(schemaName)) {
            "Schema name already in use: $schemaName"
        }
        val now = LocalDateTime.now()
        return store.save(
            Tenant(
                tenantId = request.tenantId,
                name = request.name,
                schemaName = schemaName,
                status = TenantStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun suspendTenant(tenantId: String): Tenant {
        val tenant = requireNotNull(store.findByTenantId(tenantId)) { "Tenant not found: $tenantId" }
        return store.save(tenant.copy(status = TenantStatus.SUSPENDED, updatedAt = LocalDateTime.now()))
    }

    fun activateTenant(tenantId: String): Tenant {
        val tenant = requireNotNull(store.findByTenantId(tenantId)) { "Tenant not found: $tenantId" }
        return store.save(tenant.copy(status = TenantStatus.ACTIVE, updatedAt = LocalDateTime.now()))
    }

    fun deleteTenant(tenantId: String): Tenant {
        val tenant = requireNotNull(store.findByTenantId(tenantId)) { "Tenant not found: $tenantId" }
        return store.save(tenant.copy(status = TenantStatus.DELETED, updatedAt = LocalDateTime.now()))
    }

    fun getTenant(tenantId: String): Tenant =
        requireNotNull(store.findByTenantId(tenantId)) { "Tenant not found: $tenantId" }

    fun getAllTenants(): List<Tenant> = store.findAll()

    fun getStats(): TenantStats {
        val all = store.findAll()
        return TenantStats(
            total = all.size,
            active = all.count { it.status == TenantStatus.ACTIVE },
            suspended = all.count { it.status == TenantStatus.SUSPENDED },
            deleted = all.count { it.status == TenantStatus.DELETED }
        )
    }

    fun getCurrentTenant(): String = TenantContext.get() ?: TenantFilter.DEFAULT_TENANT
}

data class CreateTenantRequest(val tenantId: String, val name: String)

data class TenantStats(
    val total: Int,
    val active: Int,
    val suspended: Int,
    val deleted: Int
)
