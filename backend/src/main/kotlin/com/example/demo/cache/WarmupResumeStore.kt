package com.example.demo.cache

interface WarmupResumeStore {
    fun findAllIds(): List<Long>
    fun countAll(): Long
}
