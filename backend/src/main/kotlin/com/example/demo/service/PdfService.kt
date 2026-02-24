package com.example.demo.service

import com.example.demo.entity.AnalysisRequest
import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service

@Service
class PdfService {

    fun generateAnalysisReport(request: AnalysisRequest): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, out)

        document.open()

        // Fonts
        val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
        val normalFont = Font(Font.HELVETICA, 12f, Font.NORMAL)
        val metaFont = Font(Font.HELVETICA, 10f, Font.ITALIC)

        // Content
        document.add(Paragraph("AI Analysis Report", titleFont))
        document.add(
                Paragraph(
                        "Generated on: ${request.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}",
                        metaFont
                )
        )
        document.add(Paragraph("\n"))

        document.add(Paragraph("File Information:", Font(Font.HELVETICA, 14f, Font.BOLD)))
        document.add(Paragraph("Filename: ${request.originalFileName}", normalFont))
        document.add(Paragraph("Status: ${request.status}", normalFont))
        document.add(Paragraph("\n"))

        document.add(Paragraph("Analysis Result:", Font(Font.HELVETICA, 14f, Font.BOLD)))
        document.add(Paragraph(request.result ?: "No result available.", normalFont))

        document.close()
        return out.toByteArray()
    }
}
