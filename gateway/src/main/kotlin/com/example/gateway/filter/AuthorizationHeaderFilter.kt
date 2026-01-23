package com.example.gateway.filter

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.security.Key
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class AuthorizationHeaderFilter(@Value("\${jwt.secret}") private val secretKey: String) :
        AbstractGatewayFilterFactory<AuthorizationHeaderFilter.Config>(Config::class.java) {

    override fun name(): String {
        return "AuthorizationHeader"
    }

    private val logger = LoggerFactory.getLogger(AuthorizationHeaderFilter::class.java)
    private val key: Key by lazy {
        Keys.hmacShaKeyFor(secretKey.toByteArray(StandardCharsets.UTF_8))
    }

    class Config

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange: ServerWebExchange, chain: GatewayFilterChain ->
            val request = exchange.request

            var jwt = ""
            if (request.headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                val authorizationHeader = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
                jwt = authorizationHeader?.replace("Bearer ", "") ?: ""
            } else if (request.queryParams.containsKey("token")) {
                jwt = request.queryParams.getFirst("token") ?: ""
            }

            if (jwt.isBlank()) {
                return@GatewayFilter onError(
                        exchange,
                        "No authorization header or token param",
                        HttpStatus.UNAUTHORIZED
                )
            }

            try {
                // 토큰 파싱 및 검증 (한 번만 수행)
                val claims =
                        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(jwt).body

                val subject = claims.subject ?: throw JwtException("Subject is missing")

                // 유저 ID를 헤더에 추가하여 라우팅
                // SSE의 경우 헤더가 유지가 안될 수 있으므로 필요시 파라미터나 헤더 추가
                val newRequest = request.mutate().header("X-User-Id", subject).build()

                return@GatewayFilter chain.filter(exchange.mutate().request(newRequest).build())
            } catch (ex: Exception) {
                logger.error("Token validation error: ${ex.message}")
                return@GatewayFilter onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED)
            }
        }
    }

    private fun onError(
            exchange: ServerWebExchange,
            err: String,
            httpStatus: HttpStatus
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = httpStatus
        logger.error(err)
        return response.setComplete()
    }
}
