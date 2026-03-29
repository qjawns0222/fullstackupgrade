package com.example.demo.sanitization

/**
 * Abstraction over OWASP policy configuration.
 * Each implementation returns a configured PolicyFactory for a specific trust level.
 */
interface HtmlSanitizationPolicy {
    /**
     * Sanitize the given input string and return the safe output.
     * Implementations must never return null — empty string is the fallback.
     */
    fun sanitize(input: String): String

    /** Human-readable name for logging/metrics */
    val policyName: String
}
