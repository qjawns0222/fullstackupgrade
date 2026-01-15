package com.example.demo.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import java.security.Key
import java.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component

@Component
class JwtTokenProvider(
        @Value("\${jwt.secret:DisIsVerySecretKeyForJwtExampleProjectMustBeLongerThan256Bits}")
        private val secretKey: String
) {
    private val key: Key =
            Keys.hmacShaKeyFor(secretKey.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

    // 30 minutes
    private val accessTokenValidityInMilliseconds: Long = 1000 * 60 * 30

    // 7 days
    private val refreshTokenValidityInMilliseconds: Long = 1000 * 60 * 60 * 24 * 7

    fun createToken(authentication: Authentication): TokenInfo {
        val authorities = authentication.authorities.joinToString(",") { it.authority }
        val now = Date()
        val accessValidity = Date(now.time + accessTokenValidityInMilliseconds)
        val refreshValidity = Date(now.time + refreshTokenValidityInMilliseconds)

        val accessToken =
                Jwts.builder()
                        .setSubject(authentication.name)
                        .claim("auth", authorities)
                        .setIssuedAt(now)
                        .setExpiration(accessValidity)
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact()

        val refreshToken =
                Jwts.builder()
                        .setSubject(authentication.name) // Use username or userId
                        .setIssuedAt(now)
                        .setExpiration(refreshValidity)
                        .signWith(key, SignatureAlgorithm.HS256)
                        .compact()

        return TokenInfo(
                grantType = "Bearer",
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresIn = accessValidity.time
        )
    }

    fun getAuthentication(accessToken: String): Authentication {
        val claims = parseClaims(accessToken)

        if (claims["auth"] == null) {
            throw RuntimeException("권한 정보가 없는 토큰입니다.")
        }

        val authorities: Collection<GrantedAuthority> =
                claims["auth"].toString().split(",").map { SimpleGrantedAuthority(it) }

        val principal: UserDetails = User(claims.subject, "", authorities)
        return UsernamePasswordAuthenticationToken(principal, "", authorities)
    }

    fun validateToken(token: String): Boolean {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
            return true
        } catch (e: io.jsonwebtoken.security.SecurityException) {
            println("Invalid JWT Token: ${e.message}")
        } catch (e: MalformedJwtException) {
            println("Invalid JWT Token: ${e.message}")
        } catch (e: ExpiredJwtException) {
            println("Expired JWT Token: ${e.message}")
        } catch (e: UnsupportedJwtException) {
            println("Unsupported JWT Token: ${e.message}")
        } catch (e: IllegalArgumentException) {
            println("JWT claims string is empty: ${e.message}")
        }
        return false
    }

    private fun parseClaims(accessToken: String): Claims {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).body
        } catch (e: ExpiredJwtException) {
            e.claims
        }
    }
}

data class TokenInfo(
        val grantType: String,
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresIn: Long
)
