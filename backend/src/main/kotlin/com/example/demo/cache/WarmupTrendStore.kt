package com.example.demo.cache

import com.example.demo.entity.TrendStats

interface WarmupTrendStore {
    fun findTop12(): List<TrendStats>
}
