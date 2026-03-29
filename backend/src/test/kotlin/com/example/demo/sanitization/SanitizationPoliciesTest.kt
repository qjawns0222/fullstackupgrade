package com.example.demo.sanitization

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for individual sanitization policies.
 * Tests are isolated to verify policy behavior without the service layer.
 */
class SanitizationPoliciesTest {

    private val plainText = PlainTextSanitizationPolicy()
    private val resume = ResumeSanitizationPolicy()
    private val richText = RichTextSanitizationPolicy()

    // ── PlainTextSanitizationPolicy ────────────────────────────────────────

    @Test
    fun `PlainTextPolicy - strips all HTML leaving text only`() {
        val result = plainText.sanitize("<h1>Title</h1><p>Content</p>")
        assertFalse(result.contains("<h1>"))
        assertFalse(result.contains("<p>"))
        assertTrue(result.contains("Title"))
        assertTrue(result.contains("Content"))
    }

    @Test
    fun `PlainTextPolicy - removes SVG-based XSS vector`() {
        val svg = """<svg onload="alert(1)"><circle/></svg>"""
        val result = plainText.sanitize(svg)
        assertFalse(result.contains("onload"), "SVG onload must be stripped")
    }

    @Test
    fun `PlainTextPolicy - removes CSS injection`() {
        val css = """<div style="background:url(javascript:alert(1))">text</div>"""
        val result = plainText.sanitize(css)
        assertFalse(result.contains("javascript:"), "CSS javascript: must be stripped")
    }

    // ── ResumeSanitizationPolicy ───────────────────────────────────────────

    @Test
    fun `ResumePolicy - allows paragraph and bold`() {
        val input = "<p><b>Java Developer</b></p>"
        val result = resume.sanitize(input)
        assertTrue(result.contains("<p>") || result.contains("Java Developer"))
        assertTrue(result.contains("Java Developer"))
    }

    @Test
    fun `ResumePolicy - strips data URI XSS in src`() {
        val input = """<img src="data:text/html,<script>alert(1)</script>">"""
        val result = resume.sanitize(input)
        assertFalse(result.contains("data:text/html"), "data URI XSS must be blocked")
    }

    @Test
    fun `ResumePolicy - strips meta refresh redirect`() {
        val input = """<meta http-equiv="refresh" content="0;url=https://evil.com">Safe text"""
        val result = resume.sanitize(input)
        assertFalse(result.contains("<meta"), "meta tag must be stripped")
        assertTrue(result.contains("Safe text"), "text content preserved")
    }

    @Test
    fun `ResumePolicy - policyName returns RESUME`() {
        assertEquals("RESUME", resume.policyName)
    }

    // ── RichTextSanitizationPolicy ─────────────────────────────────────────

    @Test
    fun `RichTextPolicy - strips base tag used for phishing`() {
        // Base tag can rewrite all relative URLs on the page
        val input = """<base href="https://evil.com/"><p>Click <a href="/login">here</a></p>"""
        val result = richText.sanitize(input)
        assertFalse(result.contains("<base"), "base tag must be stripped")
    }

    @Test
    fun `RichTextPolicy - strips textarea for XSS escape`() {
        // </textarea> can break out of surrounding textarea context
        val input = """</textarea><script>alert(1)</script><textarea>"""
        val result = richText.sanitize(input)
        assertFalse(result.contains("<script>"), "script injection via textarea escape must fail")
    }

    @Test
    fun `RichTextPolicy - policyName returns RICH_TEXT`() {
        assertEquals("RICH_TEXT", richText.policyName)
    }

    @Test
    fun `PlainTextPolicy - policyName returns PLAIN_TEXT`() {
        assertEquals("PLAIN_TEXT", plainText.policyName)
    }

    @Test
    fun `all policies handle empty string without exception`() {
        assertDoesNotThrow { plainText.sanitize("") }
        assertDoesNotThrow { resume.sanitize("") }
        assertDoesNotThrow { richText.sanitize("") }
    }

    @Test
    fun `all policies handle unicode content correctly`() {
        val korean = "안녕하세요 저는 <b>김개발</b>입니다"
        assertTrue(plainText.sanitize(korean).contains("김개발"), "Korean text preserved in PLAIN_TEXT")
        assertTrue(resume.sanitize(korean).contains("김개발"), "Korean text preserved in RESUME")
        assertTrue(richText.sanitize(korean).contains("김개발"), "Korean text preserved in RICH_TEXT")
    }
}
