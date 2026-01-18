package com.example.demo.repository

import com.example.demo.entity.TrendStats
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository interface TrendStatsRepository : JpaRepository<TrendStats, Long>
