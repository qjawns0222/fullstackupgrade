package com.example.demo.tenant

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/tenants")
class TenantController(private val service: TenantService) {

    @GetMapping
    fun listTenants() = ResponseEntity.ok(service.getAllTenants())

    @GetMapping("/stats")
    fun getStats() = ResponseEntity.ok(service.getStats())

    @GetMapping("/current")
    fun getCurrentTenant() = ResponseEntity.ok(mapOf("tenantId" to service.getCurrentTenant()))

    @GetMapping("/{tenantId}")
    fun getTenant(@PathVariable tenantId: String) =
        ResponseEntity.ok(service.getTenant(tenantId))

    @PostMapping
    fun createTenant(@RequestBody request: CreateTenantRequest) =
        ResponseEntity.ok(service.createTenant(request))

    @PutMapping("/{tenantId}/suspend")
    fun suspendTenant(@PathVariable tenantId: String) =
        ResponseEntity.ok(service.suspendTenant(tenantId))

    @PutMapping("/{tenantId}/activate")
    fun activateTenant(@PathVariable tenantId: String) =
        ResponseEntity.ok(service.activateTenant(tenantId))

    @DeleteMapping("/{tenantId}")
    fun deleteTenant(@PathVariable tenantId: String) =
        ResponseEntity.ok(service.deleteTenant(tenantId))
}
