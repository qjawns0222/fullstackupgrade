package com.example.demo.monitoring

import io.sentry.Sentry
import io.sentry.protocol.SentryId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Sentry 연동 상태 확인 및 테스트용 엔드포인트.
 *
 * /api/sentry/health — Sentry SDK 초기화 여부와 DSN 설정 상태 반환
 * /api/sentry/test-error — 테스트 예외를 Sentry에 직접 전송 (개발/QA 용도)
 */
@RestController
@RequestMapping("/api/sentry")
class SentryErrorController {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, Any>> {
        val isEnabled = Sentry.isEnabled()
        return ResponseEntity.ok(
            mapOf(
                "sentryEnabled" to isEnabled,
                "status" to if (isEnabled) "connected" else "dsn_not_configured"
            )
        )
    }

    @PostMapping("/test-error")
    fun testError(): ResponseEntity<Map<String, Any>> {
        val eventId: SentryId = Sentry.captureMessage("Sentry 테스트 이벤트 — 무시하세요")
        return ResponseEntity.ok(
            mapOf(
                "sentryEventId" to eventId.toString(),
                "message" to "Test event sent to Sentry"
            )
        )
    }
}
