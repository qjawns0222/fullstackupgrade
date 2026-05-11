package com.example.demo.audit

import org.springframework.stereotype.Component

@Component
class JpaAuditLogStore(private val repository: AuditLogRepository) : AuditLogStore {
    override fun saveAll(documents: List<AuditLogDocument>) {
        repository.saveAll(documents)
    }
}
