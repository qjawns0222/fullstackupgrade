package com.example.demo.controller

import com.example.demo.dto.JobApplicationRequest
import com.example.demo.dto.JobApplicationResponse
import com.example.demo.repository.UserRepository
import com.example.demo.service.ExcelService
import com.example.demo.service.JobApplicationService
import java.security.Principal
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/applications")
class JobApplicationController(
        private val jobApplicationService: JobApplicationService,
        private val userRepository: UserRepository,
        private val excelService: ExcelService
) {

    private fun getUserId(principal: Principal): Long {
        val user =
                userRepository.findByUsername(principal.name).orElseThrow {
                    IllegalArgumentException("User not found")
                }
        return user.id!!
    }

    @GetMapping
    fun getAllApplications(principal: Principal): ResponseEntity<List<JobApplicationResponse>> {
        val userId = getUserId(principal)
        return ResponseEntity.ok(jobApplicationService.getAllApplications(userId))
    }

    @GetMapping("/export")
    fun exportApplications(principal: Principal): ResponseEntity<InputStreamResource> {
        val userId = getUserId(principal)
        val applications = jobApplicationService.getAllApplications(userId)
        val stream = excelService.generateJobApplicationExcel(applications)

        val headers = HttpHeaders()
        headers.add("Content-Disposition", "attachment; filename=job_applications.xlsx")

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .body(InputStreamResource(stream))
    }

    @GetMapping("/{id}")
    fun getApplication(
            @PathVariable id: Long,
            principal: Principal
    ): ResponseEntity<JobApplicationResponse> {
        val userId = getUserId(principal)
        return ResponseEntity.ok(jobApplicationService.getApplication(id, userId))
    }

    @PostMapping
    fun createApplication(
            @RequestBody request: JobApplicationRequest,
            principal: Principal
    ): ResponseEntity<JobApplicationResponse> {
        val userId = getUserId(principal)
        val response = jobApplicationService.createApplication(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{id}")
    fun updateApplication(
            @PathVariable id: Long,
            @RequestBody request: JobApplicationRequest,
            principal: Principal
    ): ResponseEntity<JobApplicationResponse> {
        val userId = getUserId(principal)
        return ResponseEntity.ok(jobApplicationService.updateApplication(id, userId, request))
    }

    @DeleteMapping("/{id}")
    fun deleteApplication(@PathVariable id: Long, principal: Principal): ResponseEntity<Unit> {
        val userId = getUserId(principal)
        jobApplicationService.deleteApplication(id, userId)
        return ResponseEntity.noContent().build()
    }
}
