package com.example.demo.controller

import com.example.demo.document.ResumeDocument
import com.example.demo.entity.Resume
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.ResumeSearchRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/resumes")
class ResumeController(
        private val resumeRepository: ResumeRepository,
        private val resumeSearchRepository: ResumeSearchRepository
) {

    // 1. RDB Search (QueryDSL)
    @GetMapping("/search/rdb")
    fun searchRdb(
            @RequestParam(required = false) keyword: String?,
            @PageableDefault(size = 10) pageable: Pageable
    ): Page<Resume> {
        return resumeRepository.search(keyword, pageable)
    }

    // 2. Elasticsearch Search (Text or Chosung)
    @GetMapping("/search/es")
    fun searchEs(@RequestParam keyword: String): List<ResumeDocument> {
        // Simple logic: If keyword is all consonants, assume Chosung search
        System.out.println("keyword: $keyword")
        val isChosung = keyword.all { it in 'ㄱ'..'ㅎ' }
        return if (isChosung) {
            resumeSearchRepository.findByContentChosungContaining(keyword)
        } else {
            resumeSearchRepository.findByContentContaining(keyword)
        }
    }
}
