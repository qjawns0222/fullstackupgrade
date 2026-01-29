package com.example.demo.service

import com.example.demo.dto.JobApplicationResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service

@Service
class ExcelService {

    fun generateJobApplicationExcel(
            applications: List<JobApplicationResponse>
    ): ByteArrayInputStream {
        val workbook: Workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Job Applications")

        val headerRow = sheet.createRow(0)
        val headers = arrayOf("ID", "Company Name", "Position", "Status", "Applied Date", "Memo")

        for ((index, header) in headers.withIndex()) {
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
        }

        for ((index, app) in applications.withIndex()) {
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(app.id.toDouble())
            row.createCell(1).setCellValue(app.companyName)
            row.createCell(2).setCellValue(app.position)
            row.createCell(3).setCellValue(app.status.name)
            row.createCell(4).setCellValue(app.appliedDate.toString())
            row.createCell(5).setCellValue(app.memo ?: "")
        }

        for (i in headers.indices) {
            sheet.autoSizeColumn(i)
        }

        val out = ByteArrayOutputStream()
        workbook.write(out)
        workbook.close()

        return ByteArrayInputStream(out.toByteArray())
    }
}
