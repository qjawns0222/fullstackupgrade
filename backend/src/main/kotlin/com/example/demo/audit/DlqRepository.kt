package com.example.demo.audit

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DlqRepository : JpaRepository<DlqMessage, Long> {

    fun findByDlqStatus(status: DlqStatus, pageable: Pageable): Page<DlqMessage>

    @Query("SELECT COUNT(d) FROM DlqMessage d WHERE d.dlqStatus = :status")
    fun countByDlqStatus(status: DlqStatus): Long

    @Query("""
        SELECT new com.example.demo.audit.DlqStats(
            COUNT(d),
            SUM(CASE WHEN d.dlqStatus = 'PENDING' THEN 1 ELSE 0 END),
            SUM(CASE WHEN d.dlqStatus = 'RESOLVED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN d.dlqStatus = 'DISCARDED' THEN 1 ELSE 0 END)
        ) FROM DlqMessage d
    """)
    fun getStats(): DlqStats
}
