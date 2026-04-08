package com.example.demo.apichange

import org.springframework.data.jpa.repository.JpaRepository

interface ApiBreakingChangeRepository : JpaRepository<ApiBreakingChange, Long> {
    fun findByOldVersionAndNewVersion(oldVersion: String, newVersion: String): List<ApiBreakingChange>
    fun findAllByOrderByDetectedAtDesc(): List<ApiBreakingChange>
}
