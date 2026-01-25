package com.example.demo.repository

import com.example.demo.entity.JobApplication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface JobApplicationRepository : JpaRepository<JobApplication, Long> {
    fun findAllByUserId(userId: Long): List<JobApplication>
}
