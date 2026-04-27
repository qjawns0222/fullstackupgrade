package com.example.demo.scoring

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class ScoringRequest(val analysisRequestId: Long, val resumeText: String, val jobTitle: String)

@RestController
@RequestMapping("/api/scoring")
class ResumeScoringController(private val service: ResumeScoringService) {

    @PostMapping
    fun score(@RequestBody request: ScoringRequest): ResponseEntity<ResumeScore> {
        val result = service.score(request.analysisRequestId, request.resumeText, request.jobTitle)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/request/{analysisRequestId}")
    fun getByRequest(@PathVariable analysisRequestId: Long): ResponseEntity<List<ResumeScore>> =
        ResponseEntity.ok(service.getScores(analysisRequestId))

    @GetMapping
    fun getAll(): ResponseEntity<List<ResumeScore>> =
        ResponseEntity.ok(service.getAllScores())
}
