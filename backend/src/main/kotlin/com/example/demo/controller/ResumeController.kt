package com.example.demo.controller

import com.example.demo.document.ResumeDocument
import com.example.demo.entity.Resume
import com.example.demo.repository.ResumeRepository
import com.example.demo.repository.ResumeSearchRepository
import com.example.demo.repository.UserRepository
import com.example.demo.saga.ResumeSagaOrchestrator
import com.example.demo.saga.ResumeSagaResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/resumes")
class ResumeController(
        private val resumeRepository: ResumeRepository,
        private val resumeSearchRepository: ResumeSearchRepository,
        private val resumeSagaOrchestrator: ResumeSagaOrchestrator,
        private val userRepository: UserRepository
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
        System.out.println("keyword: $keyword")
        val isChosung = keyword.all { it in 'ㄱ'..'ㅎ' }
        return if (isChosung) {
            resumeSearchRepository.findByContentChosungContaining(keyword)
        } else {
            resumeSearchRepository.findByContentContaining(keyword)
        }
    }

    // 3. Saga 기반 이력서 업로드 (S3 → DB → ES 원자성 보장)
    @PostMapping("/upload")
    fun uploadResume(
            @RequestParam("file") file: MultipartFile,
            @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<ResumeSagaResult> {
        val user = userRepository.findByUsername(userDetails.username)
                .orElseThrow { RuntimeException("사용자를 찾을 수 없습니다.") }
        val result = resumeSagaOrchestrator.execute(file, user)
        return if (result.success) {
            ResponseEntity.ok(result)
        } else {
            ResponseEntity.internalServerError().body(result)
        }
    }
}
