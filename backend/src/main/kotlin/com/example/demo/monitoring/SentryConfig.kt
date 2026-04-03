package com.example.demo.monitoring

import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.spring.jakarta.tracing.SentryTracingFilter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Sentry 에러 추적 및 성능 모니터링 설정.
 *
 * SENTRY_DSN 환경변수가 없으면 전송을 막고 로컬에서만 이벤트를 처리한다.
 * SentryTracingFilter는 각 HTTP 요청을 Sentry Transaction으로 래핑해
 * 엔드포인트별 지속 시간과 에러율을 Sentry Performance 대시보드에 노출한다.
 */
@Configuration
class SentryConfig {

    private val log = LoggerFactory.getLogger(SentryConfig::class.java)

    /**
     * Sentry 트랜잭션 필터 — 모든 HTTP 요청을 Performance Transaction으로 래핑.
     * RateLimitFilter(HIGHEST_PRECEDENCE) 이후, RequestCorrelationFilter(HIGHEST_PRECEDENCE+1)
     * 이전에 실행되도록 order를 설정해 X-Request-Id가 아직 MDC에 없는 상태에서도
     * Sentry가 자체 trace-id를 생성한다.
     */
    @Bean
    fun sentryTracingFilterRegistration(): FilterRegistrationBean<SentryTracingFilter> {
        val registration = FilterRegistrationBean(SentryTracingFilter())
        registration.order = Ordered.HIGHEST_PRECEDENCE + 2
        return registration
    }

    /**
     * SentryOptions 커스터마이저 — BeforeSend 훅으로 민감 데이터를 마스킹하고
     * 500 미만의 예외는 breadcrumb에만 남겨 이벤트 쿼터를 절약한다.
     */
    @Bean
    fun sentryOptionsCustomizer(): SentryOptions.BeforeSendCallback {
        return SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
            // Authorization 헤더가 실수로 포함됐을 경우 마스킹
            event.request?.headers?.remove("Authorization")
            event.request?.headers?.remove("Cookie")
            event
        }
    }
}
