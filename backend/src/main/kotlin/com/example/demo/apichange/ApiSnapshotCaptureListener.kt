package com.example.demo.apichange

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class ApiSnapshotCaptureListener(
    private val detectionService: ApiChangeDetectionService,
    private val objectMapper: ObjectMapper,
    private val applicationContext: org.springframework.context.ApplicationContext
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate()

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            val port = resolvePort()
            val specJson = restTemplate.getForObject(
                "http://localhost:$port/v3/api-docs",
                String::class.java
            ) ?: run {
                log.warn("[API-DIFF] /v3/api-docs 응답이 비어있습니다.")
                return
            }

            val version = extractVersion(specJson)
            val result = detectionService.captureAndCompare(specJson, version)

            if (!result.compatible && result.breakingChanges.isNotEmpty()) {
                log.warn("=== API BREAKING CHANGES DETECTED ({}건) ===", result.breakingChanges.size)
                result.breakingChanges.forEach { bc ->
                    log.warn("  [{}] {}", bc.changeType, bc.description)
                }
            }
        } catch (e: Exception) {
            log.error("[API-DIFF] 스냅샷 캡처 실패: ${e.message}", e)
        }
    }

    private fun resolvePort(): Int {
        return try {
            val webServer = (applicationContext as ServletWebServerApplicationContext).webServer
            webServer.port
        } catch (e: Exception) {
            8080
        }
    }

    private fun extractVersion(specJson: String): String {
        return try {
            objectMapper.readTree(specJson).path("info").path("version").asText("unknown")
        } catch (e: Exception) {
            "unknown"
        }
    }
}
