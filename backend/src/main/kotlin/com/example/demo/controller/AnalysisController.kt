package com.example.demo.controller

import com.example.demo.entity.AnalysisRequest
import com.example.demo.event.AiAnalysisEvent
import com.example.demo.repository.AnalysisRequestRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal

@RestController
@RequestMapping("/api/analysis")
class AnalysisController(
    private val repository: AnalysisRequestRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    @PostMapping
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        principal: Principal?
    ): ResponseEntity<Map<String, Any>> {
        // 1. Save initial record
        // originalFilename can be null, handle it
        val fileName = file.originalFilename ?: "unknown_file"
        val request = AnalysisRequest(fileName)
        val savedRequest = repository.save(request)

        val username = principal?.name ?: "anonymous"

        // 2. Publish event (Async processing starts here)
        // savedRequest.id should be accessible if it generated.
        // In Kotlin, id is nullable because it's initially null.
        // But after save, it should be populated. We can use safe call or !! if confident.
        val requestId = savedRequest.id!!
        eventPublisher.publishEvent(AiAnalysisEvent(requestId, username))

        // 3. Return ID immediately
        val response = HashMap<String, Any>()
        response["id"] = requestId
        response["message"] = "File uploaded and analysis started."

        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getStatus(@PathVariable id: Long): ResponseEntity<AnalysisRequest> {
        return repository.findById(id)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.notFound().build())
    }
}
