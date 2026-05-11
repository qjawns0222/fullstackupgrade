package com.example.demo.audit

interface AuditLogStore {
    fun saveAll(documents: List<AuditLogDocument>)
}
