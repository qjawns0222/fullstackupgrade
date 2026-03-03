package com.example.demo.analysis

import org.springframework.data.jpa.repository.JpaRepository

interface AnalysisRequestRepository : JpaRepository<AnalysisRequest, Long>
