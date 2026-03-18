package com.example.demo.graphql.query

import com.example.demo.entity.TrendStats
import com.example.demo.repository.TrendStatsRepository
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class TrendStatsQueryController(
    private val trendStatsRepository: TrendStatsRepository
) {

    @QueryMapping
    fun trends(): List<TrendStats> = trendStatsRepository.findAll()
}
