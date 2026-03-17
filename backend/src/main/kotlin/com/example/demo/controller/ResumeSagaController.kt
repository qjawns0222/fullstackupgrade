package com.example.demo.controller

import com.example.demo.saga.ResumeSagaOrchestrator
import com.example.demo.saga.ResumeSagaState
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/saga/resume")
class ResumeSagaController(
    private val resumeSagaOrchestrator: ResumeSagaOrchestrator
) {

    @GetMapping("/{sagaId}")
    fun getSagaState(@PathVariable sagaId: String): ResponseEntity<ResumeSagaState> {
        val state = resumeSagaOrchestrator.getSagaState(sagaId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(state)
    }

    @GetMapping("/active")
    fun getActiveSagas(): ResponseEntity<List<String>> {
        val keys = resumeSagaOrchestrator.getActiveSagaKeys()
        return ResponseEntity.ok(keys)
    }
}
