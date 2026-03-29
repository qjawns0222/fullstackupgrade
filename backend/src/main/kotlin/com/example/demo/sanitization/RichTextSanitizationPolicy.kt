package com.example.demo.sanitization

import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Rich text policy for job descriptions and announcements.
 * Permits structural and formatting HTML, safe links (no javascript:), and basic tables.
 *
 * Production risk addressed: Job description fields rendered in the frontend can contain
 * user-submitted HTML. Without policy enforcement, href="javascript:void(0)" or
 * data: URI XSS vectors bypass naive blacklist filters.
 */
@Component
class RichTextSanitizationPolicy : HtmlSanitizationPolicy {

    private val safeUrlPattern: Pattern = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE)

    private val factory: PolicyFactory = HtmlPolicyBuilder()
        .allowElements(
            "b", "strong", "i", "em", "u", "s", "strike",
            "p", "br", "hr", "span", "div",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "ul", "ol", "li", "dl", "dt", "dd",
            "table", "thead", "tbody", "tr", "th", "td",
            "blockquote", "pre", "code", "a"
        )
        .allowAttributes("class", "id").globally()
        .allowAttributes("href").matching(safeUrlPattern).onElements("a")
        .allowAttributes("target").onElements("a")
        .allowAttributes("colspan", "rowspan").onElements("td", "th")
        .disallowElements(
            "script", "iframe", "object", "embed", "applet",
            "form", "input", "button", "select", "textarea",
            "link", "style", "meta", "base", "svg", "math"
        )
        .toFactory()

    override val policyName: String = "RICH_TEXT"

    override fun sanitize(input: String): String = factory.sanitize(input)
}
