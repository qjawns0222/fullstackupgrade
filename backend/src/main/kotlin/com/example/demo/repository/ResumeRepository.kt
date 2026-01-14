package com.example.demo.repository

import com.example.demo.entity.Resume
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeRepository : JpaRepository<Resume, Long>, ResumeRepositoryCustom
