package com.example.demo.service

import java.io.File
import net.sourceforge.tess4j.ITesseract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class OcrServiceTest {

    @Mock lateinit var tesseract: ITesseract

    @InjectMocks lateinit var ocrService: OcrService

    @Test
    fun `should perform OCR successfully`() {
        // Given
        val mockData = "dummy-image-content".toByteArray()
        val expectedText = "Extracted Text"

        // We use any(File::class.java) but since we can't easily capture the temp file
        // in this simple mock, we just trust the logic for now.
        // Actually, Tess4J doesn't have a simple any File matcher without ArgumentMatchers.
        // For simplicity in this environment, let's mock it carefully.
        `when`(tesseract.doOCR(org.mockito.ArgumentMatchers.any(File::class.java)))
                .thenReturn(expectedText)

        // When
        val result = ocrService.doOcr(mockData)

        // Then
        assertEquals(expectedText, result)
    }
}
