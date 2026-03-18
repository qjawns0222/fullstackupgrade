package com.example.demo.analysis

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

    @Mock lateinit var redisTemplate: org.springframework.data.redis.core.StringRedisTemplate

    @Mock lateinit var valueOperations: org.springframework.data.redis.core.ValueOperations<String, String>

    @InjectMocks lateinit var ocrService: OcrService

    @Test
    fun `should perform OCR successfully`() {
        // Given
        val mockData = "dummy-image-content".toByteArray()
        val expectedText = "Extracted Text"

        `when`(redisTemplate.opsForValue()).thenReturn(valueOperations)
        `when`(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null)
        `when`(tesseract.doOCR(org.mockito.ArgumentMatchers.any(File::class.java)))
                .thenReturn(expectedText)

        // When
        val result = ocrService.doOcr(mockData)

        // Then
        assertEquals(expectedText, result)
    }
}
