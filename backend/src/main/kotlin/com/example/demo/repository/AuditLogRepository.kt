package com.example.demo.repository

import com.example.demo.document.AuditLogDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository interface AuditLogRepository : ElasticsearchRepository<AuditLogDocument, String>
