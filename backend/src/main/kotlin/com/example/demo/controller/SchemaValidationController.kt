package com.example.demo.controller

import com.example.demo.validation.SchemaViolation
import com.example.demo.validation.ViolationStats
import com.example.demo.validation.ViolationStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/schema-validation")
class SchemaValidationController(
    private val violationStore: ViolationStore
) {

    @GetMapping("/violations")
    fun getViolations(@RequestParam(defaultValue = "50") limit: Int): List<SchemaViolation> {
        return violationStore.getRecent(limit)
    }

    @GetMapping("/stats")
    fun getStats(): ViolationStats {
        return violationStore.getStats()
    }

    @DeleteMapping("/violations")
    fun clearViolations(): ResponseEntity<Void> {
        violationStore.clear()
        return ResponseEntity.noContent().build()
    }
}
