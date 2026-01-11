package com.example.demo.repository;

import com.example.demo.entity.AnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long> {
}
