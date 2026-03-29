package com.example.demo.sanitization

import org.owasp.html.HtmlPolicyBuilder
import org.springframework.stereotype.Component

/**
 * Strips ALL HTML tags and attributes.
 * Use for plain text fields: names, email subjects, search keywords.
 *
 * Production risk addressed: stored XSS via form fields that get rendered in admin UIs.
 */
@Component
class PlainTextSanitizationPolicy : HtmlSanitizationPolicy {

    // Empty policy builder → allows nothing → strips all HTML tags, keeps only text nodes
    private val factory = HtmlPolicyBuilder().toFactory()

    override val policyName: String = "PLAIN_TEXT"

    override fun sanitize(input: String): String = factory.sanitize(input)
}
