package com.example.demo.controller

import com.example.demo.dto.NotificationMessage
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class NotificationController(private val template: SimpMessagingTemplate) {

    @PostMapping("/api/notify")
    fun sendNotification(@RequestBody message: NotificationMessage) {
        template.convertAndSend("/topic/notifications", message)
    }
}
