package com.example.demo.baggage

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/baggage")
class BaggageController(private val baggageContextHolder: BaggageContextHolder) {

    @GetMapping("/current")
    fun getCurrent(): ResponseEntity<BaggageContext> {
        return ResponseEntity.ok(baggageContextHolder.get())
    }

    @PostMapping("/set")
    fun set(@RequestBody request: SetBaggageRequest): ResponseEntity<BaggageContext> {
        baggageContextHolder.set(userId = request.userId, tenantId = request.tenantId)
        return ResponseEntity.ok(baggageContextHolder.get())
    }

    @GetMapping("/snapshot")
    fun snapshot(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(baggageContextHolder.snapshot())
    }
}

data class SetBaggageRequest(
    val userId: String?,
    val tenantId: String?
)
