package com.example.demo.controller

import com.example.demo.service.DashboardData
import com.example.demo.service.DashboardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(private val dashboardService: DashboardService) {

    @GetMapping("/{userId}")
    fun getDashboardData(@PathVariable userId: String): DashboardData {
        return dashboardService.getDashboardData(userId)
    }
}
