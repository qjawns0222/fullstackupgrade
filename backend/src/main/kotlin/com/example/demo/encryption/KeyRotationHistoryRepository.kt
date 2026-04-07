package com.example.demo.encryption

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface KeyRotationHistoryRepository : JpaRepository<KeyRotationHistory, Long>
