package com.example.demo.apichange

interface ApiSnapshotStore {
    fun save(snapshot: ApiSnapshot): ApiSnapshot
    fun findLatest(): ApiSnapshot?
    fun findAllDesc(): List<ApiSnapshot>
}

interface ApiBreakingChangeStore {
    fun saveAll(changes: List<ApiBreakingChange>): List<ApiBreakingChange>
    fun findAll(): List<ApiBreakingChange>
    fun findAllDesc(): List<ApiBreakingChange>
    fun findBetween(oldVersion: String, newVersion: String): List<ApiBreakingChange>
}
