package com.example.demo.service

import com.example.demo.dto.JobApplicationResponse
import com.example.demo.entity.JobApplicationStatus
import java.io.ByteArrayInputStream
import java.time.LocalDate
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExcelServiceTest {

    private val excelService = ExcelService()

    @Test
    fun `generateJobApplicationExcel should generate valid excel file`() {
        // Given
        val applications =
                listOf(
                        JobApplicationResponse(
                                id = 1,
                                companyName = "Google",
                                position = "Software Engineer",
                                status = JobApplicationStatus.APPLIED,
                                appliedDate = LocalDate.now(),
                                memo = "Referral",
                                userId = 1
                        ),
                        JobApplicationResponse(
                                id = 2,
                                companyName = "Amazon",
                                position = "Backend Dev",
                                status = JobApplicationStatus.INTERVIEW,
                                appliedDate = LocalDate.now().minusDays(1),
                                memo = null,
                                userId = 1
                        )
                )

        // When
        val inputStream: ByteArrayInputStream =
                excelService.generateJobApplicationExcel(applications)

        // Then
        assertNotNull(inputStream)

        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheet("Job Applications")
        assertNotNull(sheet)

        // Check Header
        val headerRow = sheet.getRow(0)
        assertEquals("Company Name", headerRow.getCell(1).stringCellValue)

        // Check Data Row 1
        val row1 = sheet.getRow(1)
        assertEquals("Google", row1.getCell(1).stringCellValue)
        assertEquals("APPLIED", row1.getCell(3).stringCellValue)

        // Check Data Row 2
        val row2 = sheet.getRow(2)
        assertEquals("Amazon", row2.getCell(1).stringCellValue)

        workbook.close()
    }
}
