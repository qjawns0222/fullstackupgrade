package com.example.demo.service

import com.example.demo.entity.AnalysisRequest
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfServiceTest {

    private val pdfService = PdfService()

    @Test
    fun `should generate non-empty PDF byte array`() {
        // Given
        val request = AnalysisRequest("test_file.png", "key-123")
        request.id = 1L
        request.complete("This is a test analysis result content.")
        request.createdAt = LocalDateTime.now()

        // When
        val pdfBytes = pdfService.generateAnalysisReport(request)

        // Then
        assertTrue(pdfBytes.isNotEmpty(), "PDF byte array should not be empty")
        // Basic PDF header check: %PDF-
        val header = String(pdfBytes.sliceArray(0..4))
        assertTrue(header.contains("%PDF"), "Should have PDF header")
    }
}
