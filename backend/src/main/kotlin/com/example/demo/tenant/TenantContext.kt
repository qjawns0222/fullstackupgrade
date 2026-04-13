package com.example.demo.tenant

object TenantContext {
    private val current = ThreadLocal<String>()

    fun set(tenantId: String) = current.set(tenantId)
    fun get(): String? = current.get()
    fun clear() = current.remove()
}
