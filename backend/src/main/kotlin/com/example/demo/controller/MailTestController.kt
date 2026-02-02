package com.example.demo.controller

import com.example.demo.entity.TrendStats
import com.example.demo.service.MailService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mail")
class MailTestController(private val mailService: MailService) {

    @PostMapping("/test")
    fun sendTestMail(@RequestParam email: String) {
        val dummyTrends =
                listOf(
                        TrendStats(
                                techStack = "Java",
                                count = 10,
                                recordedAt = java.time.LocalDateTime.now()
                        ),
                        TrendStats(
                                techStack = "Kotlin",
                                count = 20,
                                recordedAt = java.time.LocalDateTime.now()
                        )
                )
        mailService.sendWeeklyReport(email, "Tester", dummyTrends)
    }
}
