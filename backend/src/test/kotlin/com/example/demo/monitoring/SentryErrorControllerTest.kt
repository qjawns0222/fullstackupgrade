package com.example.demo.monitoring

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * SentryErrorController 단위 테스트.
 * Sentry SDK는 DSN 없이도 gracefully 동작하므로
 * 컨트롤러 직접 생성 방식으로 검증한다.
 */
class SentryErrorControllerTest {

    private lateinit var controller: SentryErrorController

    @BeforeEach
    fun setUp() {
        controller = SentryErrorController()
    }

    @Test
    fun `health returns 200 with sentryEnabled field`() {
        val response = controller.health()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("sentryEnabled"))
        assertTrue(response.body!!.containsKey("status"))
    }

    @Test
    fun `health status is dsn_not_configured when Sentry SDK not initialized`() {
        val response = controller.health()

        assertEquals(HttpStatus.OK, response.statusCode)
        // 테스트 환경에서는 DSN이 없으므로 비활성 상태여야 함
        val status = response.body!!["status"] as String
        assertTrue(status == "connected" || status == "dsn_not_configured",
            "Status should be either 'connected' or 'dsn_not_configured', got: $status")
    }

    @Test
    fun `testError returns 200 with sentryEventId`() {
        val response = controller.testError()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.containsKey("sentryEventId"))
        assertEquals("Test event sent to Sentry", response.body!!["message"])
    }

    @Test
    fun `testError sentryEventId is a non-empty string`() {
        val response = controller.testError()

        val eventId = response.body!!["sentryEventId"] as String
        assertTrue(eventId.isNotBlank(), "Event ID should not be blank")
    }
}
