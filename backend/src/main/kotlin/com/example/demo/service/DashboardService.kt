package com.example.demo.service

import java.io.Serializable
import java.time.LocalDateTime
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class DashboardService {

    @Cacheable(value = ["dashboard"], key = "#userId")
    fun getDashboardData(userId: String): DashboardData {
        // Simulate slow DB query or AI processing
        Thread.sleep(1000)

        return DashboardData(
                userId = userId,
                data = "Expensive Data for $userId",
                timestamp = LocalDateTime.now().toString()
        )
    }
}

data class DashboardData(
        val userId: String = "",
        val data: String = "",
        val timestamp: String = ""
) : Serializable
