package com.example.demo.monitoring

import io.sentry.Sentry
import io.sentry.protocol.User
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 매 요청마다 Sentry scope에 컨텍스트 정보를 주입한다.
 *
 * - 인증된 사용자 정보를 Sentry User로 설정해 에러 발생 시 누가 겪었는지 추적 가능
 * - RequestCorrelationFilter가 생성한 requestId를 Sentry tag로 추가해
 *   Sentry 이벤트와 ELK 로그를 requestId로 교차 조회할 수 있다
 *
 * Order: RequestCorrelationFilter(HIGHEST_PRECEDENCE+1) 이후에 실행해
 * MDC에서 requestId를 읽을 수 있도록 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class SentryContextFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        try {
            Sentry.configureScope { scope ->
                // requestId — ELK 로그와 교차 조회용
                val requestId = MDC.get("requestId")
                if (!requestId.isNullOrBlank()) {
                    scope.setTag("request_id", requestId)
                }

                // 인증된 사용자 정보 주입
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication != null && authentication.isAuthenticated &&
                    authentication.name != "anonymousUser"
                ) {
                    val sentryUser = User().apply {
                        username = authentication.name
                        ipAddress = resolveClientIp(request)
                    }
                    scope.user = sentryUser
                }

                // HTTP 컨텍스트 태그
                scope.setTag("http.method", request.method)
                scope.setTag("http.path", request.requestURI)
            }

            chain.doFilter(request, response)

        } finally {
            // 요청 처리 후 scope 초기화 (thread reuse 대비)
            Sentry.configureScope { scope ->
                scope.user = null
                scope.removeTag("request_id")
                scope.removeTag("http.method")
                scope.removeTag("http.path")
            }
        }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (forwarded.isNullOrBlank()) request.remoteAddr
        else forwarded.split(",").first().trim()
    }
}
