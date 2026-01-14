package com.example.demo.repository

import com.example.demo.entity.Resume
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ResumeRepositoryCustom {
    fun search(keyword: String?, pageable: Pageable): Page<Resume>
}
