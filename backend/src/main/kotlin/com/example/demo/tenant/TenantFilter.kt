package com.example.demo.tenant

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * X-Tenant-ID 요청 헤더에서 테넌트 식별자를 추출해 TenantContext에 설정한다.
 * 요청 처리 후 반드시 컨텍스트를 초기화한다 (ThreadLocal 누수 방지).
 */
@Component
@Order(1)
class TenantFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TENANT_HEADER = "X-Tenant-ID"
        const val DEFAULT_TENANT = "default"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val tenantId = request.getHeader(TENANT_HEADER)?.takeIf { it.isNotBlank() } ?: DEFAULT_TENANT
        try {
            TenantContext.set(tenantId)
            log.debug("Tenant context set to: {}", tenantId)
            chain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }
}
