package com.example.demo.repository

import com.example.demo.entity.AnalysisRequest
import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisRequestRepository : JpaRepository<AnalysisRequest, Long>
