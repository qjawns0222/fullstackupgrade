package com.example.demo.cache

import com.example.demo.entity.TrendStats
import com.example.demo.repository.TrendStatsRepository
import org.springframework.stereotype.Component

@Component
class JpaWarmupTrendStore(
    private val trendStatsRepository: TrendStatsRepository
) : WarmupTrendStore {

    override fun findTop12(): List<TrendStats> =
        trendStatsRepository.findTop12ByOrderByRecordedAtDesc()
}
