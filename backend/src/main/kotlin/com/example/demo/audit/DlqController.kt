package com.example.demo.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dlq")
class DlqController(private val dlqMonitorService: DlqMonitorService) {

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: DlqStatus?
    ): Page<DlqMessageResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "failedAt"))
        return dlqMonitorService.listMessages(status, pageable).map { it.toResponse() }
    }

    @GetMapping("/stats")
    fun stats(): DlqStats = dlqMonitorService.getStats()

    @PostMapping("/{id}/retry")
    fun retry(@PathVariable id: Long): ResponseEntity<DlqMessageResponse> {
        return ResponseEntity.ok(dlqMonitorService.retry(id).toResponse())
    }

    @PostMapping("/retry-all")
    fun retryAll(): ResponseEntity<Map<String, Int>> {
        val count = dlqMonitorService.retryAll()
        return ResponseEntity.ok(mapOf("retried" to count))
    }

    @PostMapping("/{id}/discard")
    fun discard(@PathVariable id: Long): ResponseEntity<DlqMessageResponse> {
        return ResponseEntity.ok(dlqMonitorService.discard(id).toResponse())
    }

    private fun DlqMessage.toResponse() = DlqMessageResponse(
        id = id,
        userId = userId,
        action = action,
        description = description,
        status = status,
        dlqStatus = dlqStatus,
        failedAt = failedAt.toString(),
        resolvedAt = resolvedAt?.toString(),
        retryCount = retryCount,
        lastError = lastError,
        errorMessage = errorMessage
    )
}

data class DlqMessageResponse(
    val id: Long,
    val userId: String,
    val action: String,
    val description: String,
    val status: String,
    val dlqStatus: DlqStatus,
    val failedAt: String,
    val resolvedAt: String?,
    val retryCount: Int,
    val lastError: String?,
    val errorMessage: String?
)
