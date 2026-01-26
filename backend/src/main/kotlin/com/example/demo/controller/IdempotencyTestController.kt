package com.example.demo.controller

import com.example.demo.annotation.Idempotent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test/idempotency")
class IdempotencyTestController {

    @PostMapping
    @Idempotent(expireTime = 5)
    fun testIdempotency(@RequestBody body: Map<String, String>): Map<String, String> {
        // Simulate processing
        return mapOf(
                "status" to "success",
                "message" to "Processed successfully",
                "data" to (body["data"] ?: "no-data")
        )
    }
}
