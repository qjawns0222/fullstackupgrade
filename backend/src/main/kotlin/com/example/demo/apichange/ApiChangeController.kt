package com.example.demo.apichange

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/api-changes")
class ApiChangeController(
    private val detectionService: ApiChangeDetectionService
) {

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<ChangeStats> =
        ResponseEntity.ok(detectionService.getStats())

    @GetMapping("/breaking")
    fun getAllBreakingChanges(): ResponseEntity<List<BreakingChangeResponse>> {
        val changes = detectionService.getAllBreakingChanges().map { it.toResponse() }
        return ResponseEntity.ok(changes)
    }

    @GetMapping("/breaking/between")
    fun getBreakingChangesBetween(
        @RequestParam oldVersion: String,
        @RequestParam newVersion: String
    ): ResponseEntity<List<BreakingChangeResponse>> {
        val changes = detectionService.getBreakingChangesBetween(oldVersion, newVersion).map { it.toResponse() }
        return ResponseEntity.ok(changes)
    }

    @GetMapping("/snapshots")
    fun getSnapshots(): ResponseEntity<List<SnapshotResponse>> {
        val snapshots = detectionService.getAllSnapshots().map {
            SnapshotResponse(id = it.id, version = it.version, createdAt = it.createdAt.toString())
        }
        return ResponseEntity.ok(snapshots)
    }

    @PostMapping("/compare")
    fun triggerCompare(@RequestBody request: CompareRequest): ResponseEntity<CompareResultResponse> {
        val result = detectionService.captureAndCompare(request.specJson, request.version)
        return ResponseEntity.ok(
            CompareResultResponse(
                oldVersion = result.oldVersion,
                newVersion = result.newVersion,
                compatible = result.compatible,
                breakingChangeCount = result.breakingChanges.size,
                breakingChanges = result.breakingChanges.map { it.toResponse() }
            )
        )
    }
}

private fun ApiBreakingChange.toResponse() = BreakingChangeResponse(
    id = id,
    oldVersion = oldVersion,
    newVersion = newVersion,
    changeType = changeType,
    description = description,
    element = element,
    detectedAt = detectedAt.toString()
)

data class BreakingChangeResponse(
    val id: Long,
    val oldVersion: String,
    val newVersion: String,
    val changeType: String,
    val description: String,
    val element: String?,
    val detectedAt: String
)

data class SnapshotResponse(
    val id: Long,
    val version: String,
    val createdAt: String
)

data class CompareRequest(
    val specJson: String,
    val version: String
)

data class CompareResultResponse(
    val oldVersion: String?,
    val newVersion: String,
    val compatible: Boolean,
    val breakingChangeCount: Int,
    val breakingChanges: List<BreakingChangeResponse>
)
