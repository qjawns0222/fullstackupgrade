package com.example.demo.tenant

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TenantServiceTest {

    private lateinit var store: FakeTenantStore
    private lateinit var service: TenantService

    @BeforeEach
    fun setUp() {
        store = FakeTenantStore()
        service = TenantService(store)
    }

    @Test
    fun `테넌트 생성 시 스키마명이 자동으로 생성된다`() {
        val result = service.createTenant(CreateTenantRequest("acme-corp", "Acme Corporation"))

        assertThat(result.tenantId).isEqualTo("acme-corp")
        assertThat(result.schemaName).isEqualTo("tenant_acme_corp")
        assertThat(result.status).isEqualTo(TenantStatus.ACTIVE)
    }

    @Test
    fun `중복 tenantId로 테넌트 생성 시 예외가 발생한다`() {
        service.createTenant(CreateTenantRequest("acme", "Acme"))

        assertThatThrownBy { service.createTenant(CreateTenantRequest("acme", "Acme 2")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun `테넌트 정지 시 상태가 SUSPENDED로 변경된다`() {
        service.createTenant(CreateTenantRequest("tenant1", "Tenant One"))

        val result = service.suspendTenant("tenant1")

        assertThat(result.status).isEqualTo(TenantStatus.SUSPENDED)
    }

    @Test
    fun `정지된 테넌트를 활성화하면 ACTIVE 상태가 된다`() {
        service.createTenant(CreateTenantRequest("tenant2", "Tenant Two"))
        service.suspendTenant("tenant2")

        val result = service.activateTenant("tenant2")

        assertThat(result.status).isEqualTo(TenantStatus.ACTIVE)
    }

    @Test
    fun `테넌트 삭제 시 상태가 DELETED로 변경된다`() {
        service.createTenant(CreateTenantRequest("tenant3", "Tenant Three"))

        val result = service.deleteTenant("tenant3")

        assertThat(result.status).isEqualTo(TenantStatus.DELETED)
    }

    @Test
    fun `존재하지 않는 tenantId로 조회 시 예외가 발생한다`() {
        assertThatThrownBy { service.getTenant("nonexistent") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not found")
    }

    @Test
    fun `getStats는 상태별 테넌트 수를 반환한다`() {
        service.createTenant(CreateTenantRequest("t1", "T1"))
        service.createTenant(CreateTenantRequest("t2", "T2"))
        service.createTenant(CreateTenantRequest("t3", "T3"))
        service.suspendTenant("t2")
        service.deleteTenant("t3")

        val stats = service.getStats()

        assertThat(stats.total).isEqualTo(3)
        assertThat(stats.active).isEqualTo(1)
        assertThat(stats.suspended).isEqualTo(1)
        assertThat(stats.deleted).isEqualTo(1)
    }

    @Test
    fun `getAllTenants는 전체 테넌트 목록을 반환한다`() {
        service.createTenant(CreateTenantRequest("a", "A"))
        service.createTenant(CreateTenantRequest("b", "B"))

        val all = service.getAllTenants()

        assertThat(all).hasSize(2)
    }
}

// ── Fake Store ─────────────────────────────────────────────────────────────────

class FakeTenantStore : TenantStore {
    private val data = mutableListOf<Tenant>()
    private var idSeq = 1L

    override fun save(tenant: Tenant): Tenant {
        data.removeIf { it.id == tenant.id && tenant.id != 0L }
        val saved = if (tenant.id == 0L) tenant.copy(id = idSeq++) else tenant
        data.add(saved)
        return saved
    }

    override fun findByTenantId(tenantId: String) = data.firstOrNull { it.tenantId == tenantId }
    override fun findAll() = data.sortedByDescending { it.createdAt }
    override fun findByStatus(status: TenantStatus) = data.filter { it.status == status }
    override fun existsByTenantId(tenantId: String) = data.any { it.tenantId == tenantId }
    override fun existsBySchemaName(schemaName: String) = data.any { it.schemaName == schemaName }
}
