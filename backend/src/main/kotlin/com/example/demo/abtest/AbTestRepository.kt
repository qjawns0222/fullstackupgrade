package com.example.demo.abtest

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface AbTestRepository : JpaRepository<AbTestResult, Long> {

    @Query("""
        SELECT a.variantName, COUNT(a) FROM AbTestResult a
        WHERE a.toggleName = :toggleName AND a.recordedAt >= :since
        GROUP BY a.variantName
    """)
    fun countByVariantSince(toggleName: String, since: LocalDateTime): List<Array<Any>>

    fun findTop50ByToggleNameOrderByRecordedAtDesc(toggleName: String): List<AbTestResult>
}
