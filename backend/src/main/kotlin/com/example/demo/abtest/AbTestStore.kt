package com.example.demo.abtest

import java.time.LocalDateTime

interface AbTestStore {
    fun save(result: AbTestResult): AbTestResult
    fun countByToggleAndVariantSince(toggleName: String, since: LocalDateTime): Map<String, Long>
    fun findRecentByToggle(toggleName: String, limit: Int): List<AbTestResult>
}
