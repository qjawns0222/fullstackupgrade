package com.example.demo.abtest

import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class JpaAbTestStore(private val repo: AbTestRepository) : AbTestStore {

    override fun save(result: AbTestResult) = repo.save(result)

    override fun countByToggleAndVariantSince(toggleName: String, since: LocalDateTime): Map<String, Long> =
        repo.countByVariantSince(toggleName, since)
            .associate { row -> row[0] as String to row[1] as Long }

    override fun findRecentByToggle(toggleName: String, limit: Int): List<AbTestResult> =
        repo.findTop50ByToggleNameOrderByRecordedAtDesc(toggleName).take(limit)
}
