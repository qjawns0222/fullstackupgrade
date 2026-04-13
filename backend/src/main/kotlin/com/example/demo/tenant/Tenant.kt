package com.example.demo.tenant

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "tenants")
data class Tenant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id", nullable = false, unique = true)
    val tenantId: String = "",

    @Column(nullable = false)
    val name: String = "",

    @Column(name = "schema_name", nullable = false, unique = true)
    val schemaName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: TenantStatus = TenantStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class TenantStatus { ACTIVE, SUSPENDED, DELETED }
