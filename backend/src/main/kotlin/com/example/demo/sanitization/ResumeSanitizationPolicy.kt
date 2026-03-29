package com.example.demo.sanitization

import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.springframework.stereotype.Component

/**
 * Allows safe formatting tags typically found in OCR-extracted resume text.
 *
 * Permitted tags: b, strong, i, em, u, p, br, ul, ol, li, span (no style attr)
 * Blocked: script, iframe, object, form, input, link, style, on* event handlers
 *
 * Production risk addressed: Tesseract OCR sometimes extracts embedded HTML from PDFs.
 * Without sanitization, a crafted PDF can inject stored XSS that executes when
 * admin staff view resumes in the frontend dashboard.
 */
@Component
class ResumeSanitizationPolicy : HtmlSanitizationPolicy {

    private val factory: PolicyFactory = HtmlPolicyBuilder()
        .allowElements("b", "strong", "i", "em", "u", "p", "br", "span")
        .allowElements("ul", "ol", "li")
        .allowElements("h1", "h2", "h3", "h4")
        .allowAttributes("class").onElements("span", "p", "li", "h1", "h2", "h3", "h4")
        .disallowElements("script", "iframe", "object", "embed", "form", "input", "button",
            "link", "style", "meta", "base", "applet", "svg", "math")
        .toFactory()

    override val policyName: String = "RESUME"

    override fun sanitize(input: String): String = factory.sanitize(input)
}
