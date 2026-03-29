package com.example.demo.sanitization

import com.example.demo.annotation.SanitizePolicy
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class SanitizeRequest(
    val input: String,
    val policy: String = "RESUME"
)

data class SanitizeResponse(
    val original: String,
    val sanitized: String,
    val policy: String,
    val changed: Boolean,
    val removedLength: Int
)

data class SanitizerStatsResponse(
    val totalSanitized: Double,
    val threatsDetected: Double
)

/**
 * REST API for the XSS sanitization demo.
 * Exposes sanitize-preview and stats endpoints consumed by the frontend page.
 */
@RestController
@RequestMapping("/api/sanitizer")
class SanitizerController(
    private val sanitizerService: HtmlSanitizerService,
    private val meterRegistry: MeterRegistry
) {

    @PostMapping("/preview")
    fun preview(@RequestBody request: SanitizeRequest): ResponseEntity<SanitizeResponse> {
        val policy = runCatching { SanitizePolicy.valueOf(request.policy.uppercase()) }
            .getOrDefault(SanitizePolicy.RESUME)

        val sanitized = sanitizerService.sanitizeNonNull(request.input, policy)

        return ResponseEntity.ok(
            SanitizeResponse(
                original = request.input,
                sanitized = sanitized,
                policy = policy.name,
                changed = sanitized != request.input,
                removedLength = request.input.length - sanitized.length
            )
        )
    }

    @GetMapping("/stats")
    fun stats(): ResponseEntity<SanitizerStatsResponse> {
        val total = meterRegistry.find("xss.sanitization.applied").counter()?.count() ?: 0.0
        val threats = meterRegistry.find("xss.sanitization.threat_detected").counter()?.count() ?: 0.0
        return ResponseEntity.ok(SanitizerStatsResponse(total, threats))
    }
}
