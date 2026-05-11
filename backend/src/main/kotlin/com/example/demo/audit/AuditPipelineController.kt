package com.example.demo.audit

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/audit/pipeline")
class AuditPipelineController(private val pipeline: ReactiveAuditPipeline) {

    @GetMapping("/stats")
    fun stats(): PipelineStats = pipeline.stats()
}
