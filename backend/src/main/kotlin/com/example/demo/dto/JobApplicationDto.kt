package com.example.demo.dto

import com.example.demo.entity.JobApplicationStatus
import java.time.LocalDate

data class JobApplicationRequest(
        val companyName: String,
        val position: String,
        val status: JobApplicationStatus,
        val appliedDate: LocalDate,
        val memo: String?
)

data class JobApplicationResponse(
        val id: Long,
        val companyName: String,
        val position: String,
        val status: JobApplicationStatus,
        val appliedDate: LocalDate,
        val memo: String?,
        val userId: Long
)
