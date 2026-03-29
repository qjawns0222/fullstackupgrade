package com.example.demo.annotation

/**
 * Marks a method parameter or a service method for HTML sanitization.
 *
 * When placed on a method, all String parameters of that method are sanitized
 * using the specified [policy] before the method body executes.
 *
 * @param policy The sanitization policy to apply (defaults to RESUME for content fields)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sanitize(
    val policy: SanitizePolicy = SanitizePolicy.RESUME
)

enum class SanitizePolicy {
    /** Strips all HTML tags — for plain text fields like names, titles */
    PLAIN_TEXT,

    /** Allows safe formatting tags (b, i, ul, li, p, br) — for resume content */
    RESUME,

    /** Allows rich formatting including links and headings — for job descriptions */
    RICH_TEXT
}
