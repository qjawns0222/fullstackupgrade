package com.example.demo.sanitization

import com.example.demo.annotation.SanitizePolicy
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Central service for HTML sanitization.
 *
 * Dispatches to the appropriate [HtmlSanitizationPolicy] based on the requested
 * [SanitizePolicy] enum value. Tracks sanitization counts via Micrometer for
 * operational observability (alerts if sanitization rate spikes — indicates active XSS attempt).
 */
@Service
class HtmlSanitizerService(
    private val plainTextPolicy: PlainTextSanitizationPolicy,
    private val resumePolicy: ResumeSanitizationPolicy,
    private val richTextPolicy: RichTextSanitizationPolicy,
    private val meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sanitizationCounter: Counter = Counter.builder("xss.sanitization.applied")
        .description("Number of times HTML sanitization was applied to user input")
        .register(meterRegistry)

    private val xssDetectedCounter: Counter = Counter.builder("xss.sanitization.threat_detected")
        .description("Number of times potentially malicious content was stripped")
        .register(meterRegistry)

    /**
     * Sanitize input using the given policy.
     * Returns the original string if null/blank (no allocation overhead).
     */
    fun sanitize(input: String?, policy: SanitizePolicy): String? {
        if (input.isNullOrBlank()) return input

        val sanitized = when (policy) {
            SanitizePolicy.PLAIN_TEXT -> plainTextPolicy.sanitize(input)
            SanitizePolicy.RESUME -> resumePolicy.sanitize(input)
            SanitizePolicy.RICH_TEXT -> richTextPolicy.sanitize(input)
        }

        sanitizationCounter.increment()

        if (sanitized != input) {
            xssDetectedCounter.increment()
            log.warn(
                "XSS threat detected and stripped [policy={}]: original_length={}, sanitized_length={}",
                policy.name, input.length, sanitized.length
            )
        }

        return sanitized
    }

    /**
     * Convenience for non-null contexts.
     */
    fun sanitizeNonNull(input: String, policy: SanitizePolicy): String =
        sanitize(input, policy) ?: input
}
