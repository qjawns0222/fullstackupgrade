package com.example.demo.apichange

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ApiSnapshotRepository : JpaRepository<ApiSnapshot, Long> {
    fun findTopByOrderByCreatedAtDesc(): ApiSnapshot?

    @Query("SELECT s FROM ApiSnapshot s ORDER BY s.createdAt DESC")
    fun findAllOrderByCreatedAtDesc(): List<ApiSnapshot>
}
