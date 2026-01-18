package com.example.demo.controller

import com.example.demo.entity.TrendStats
import com.example.demo.repository.TrendStatsRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trends")
class TrendStatsController(private val trendStatsRepository: TrendStatsRepository) {

    @GetMapping
    fun getTrends(): List<TrendStats> {
        // In a real scenario, you might want to filter by the latest date
        // or aggregate by tech stack. For now, we return all history
        // so the frontend can visualize the trend over time.
        return trendStatsRepository.findAll()
    }
}
