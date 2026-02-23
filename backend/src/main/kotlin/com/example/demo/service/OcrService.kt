package com.example.demo.service

import java.nio.file.Files
import net.sourceforge.tess4j.ITesseract
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OcrService(private val tesseract: ITesseract) {
    private val log = LoggerFactory.getLogger(OcrService::class.java)

    fun doOcr(data: ByteArray): String {
        log.info("Starting OCR processing for file of size: {} bytes", data.size)

        // Tesseract usually works better with Files or BufferedImages
        // We'll create a temporary file to process
        val tempFile = Files.createTempFile("ocr_", ".tmp").toFile()
        try {
            tempFile.writeBytes(data)

            // Perform OCR
            val result = tesseract.doOCR(tempFile)
            log.info("OCR processing completed successfully.")
            return result
        } catch (e: Exception) {
            log.error("OCR processing failed", e)
            throw RuntimeException("Failed to perform OCR: ${e.message}", e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
