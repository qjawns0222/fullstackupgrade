package com.example.demo.controller

import com.example.demo.document.AuditLogDocument
import com.example.demo.repository.AuditLogRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Audit Logs", description = "Endpoints for managing audit logs")
@RestController
@RequestMapping("/api/audit-logs")
class AuditLogController(private val auditLogRepository: AuditLogRepository) {

    @Operation(summary = "Get audit logs", description = "Retrieve paginated audit logs")
    @GetMapping
    fun getAuditLogs(
            @PageableDefault(sort = ["timestamp"], direction = Sort.Direction.DESC)
            pageable: Pageable
    ): Page<AuditLogDocument> {
        return auditLogRepository.findAll(pageable)
    }
}
