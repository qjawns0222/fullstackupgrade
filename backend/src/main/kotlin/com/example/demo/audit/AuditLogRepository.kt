package com.example.demo.audit

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository interface AuditLogRepository : ElasticsearchRepository<AuditLogDocument, String>
