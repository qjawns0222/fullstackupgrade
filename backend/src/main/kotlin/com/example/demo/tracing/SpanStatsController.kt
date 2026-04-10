package com.example.demo.tracing

import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/tracing")
class SpanStatsController(private val spanStore: SpanStore) {

    @GetMapping("/stats")
    fun stats(): SpanStats = spanStore.stats()

    @GetMapping("/recent")
    fun recent(@RequestParam(defaultValue = "50") limit: Int): List<SpanRecordDto> =
        spanStore.findRecent(limit.coerceIn(1, 100)).map(::toDto)

    @GetMapping("/slow")
    fun slow(
        @RequestParam(defaultValue = "500") thresholdMs: Long,
        @RequestParam(defaultValue = "50") limit: Int
    ): List<SpanRecordDto> =
        spanStore.findSlowSpans(thresholdMs, limit.coerceIn(1, 100)).map(::toDto)

    private fun toDto(r: SpanRecord) = SpanRecordDto(
        id = r.id,
        spanName = r.spanName,
        className = r.className,
        methodName = r.methodName,
        durationMs = r.durationMs,
        status = r.status,
        errorMessage = r.errorMessage,
        recordedAt = r.recordedAt.toString()
    )
}

data class SpanRecordDto(
    val id: Long,
    val spanName: String,
    val className: String,
    val methodName: String,
    val durationMs: Long,
    val status: String,
    val errorMessage: String?,
    val recordedAt: String
)
