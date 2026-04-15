package com.example.demo.notification

import com.example.demo.entity.JobApplicationStatus

data class ApplicationStatusChangedEvent(
    val applicationId: Long,
    val companyName: String,
    val position: String,
    val newStatus: JobApplicationStatus,
    val userId: Long,
    val timestamp: Long = System.currentTimeMillis()
)
