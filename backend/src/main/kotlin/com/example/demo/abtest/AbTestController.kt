package com.example.demo.abtest

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class AssignVariantRequest(
    val toggleName: String,
    val userId: String? = null,
    val sessionId: String? = null
)

@RestController
@RequestMapping("/api/ab-test")
class AbTestController(private val service: AbTestService) {

    @PostMapping("/assign")
    fun assignVariant(@RequestBody req: AssignVariantRequest): ResponseEntity<AbTestResult> =
        ResponseEntity.ok(service.getVariant(req.toggleName, req.userId, req.sessionId))

    @GetMapping("/stats/{toggleName}")
    fun getStats(
        @PathVariable toggleName: String,
        @RequestParam(defaultValue = "24") periodHours: Int
    ): ResponseEntity<VariantStats> =
        ResponseEntity.ok(service.getStats(toggleName, periodHours))

    @GetMapping("/results/{toggleName}")
    fun getResults(
        @PathVariable toggleName: String,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<AbTestResult>> =
        ResponseEntity.ok(service.getRecentResults(toggleName, limit))
}
