package com.example.demo.controller

import com.example.demo.annotation.RateLimit
import com.example.demo.dto.LoginRequest
import com.example.demo.dto.TokenDto
import com.example.demo.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    @RateLimit(key = "auth-login", capacity = 5, tokens = 5, seconds = 60)
    fun login(@RequestBody loginRequest: LoginRequest): ResponseEntity<TokenDto> {
        return ResponseEntity.ok(authService.login(loginRequest))
    }

    @PostMapping("/reissue")
    fun reissue(@RequestHeader("Authorization") refreshToken: String): ResponseEntity<TokenDto> {
        // Remove "Bearer " if present
        val token =
                if (refreshToken.startsWith("Bearer ")) {
                    refreshToken.substring(7)
                } else {
                    refreshToken
                }
        return ResponseEntity.ok(authService.reissue(token))
    }

    @PostMapping("/logout")
    fun logout(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Refresh-Token", required = false) refreshToken: String?
    ): ResponseEntity<Map<String, String>> {
        val accessToken = if (authorization.startsWith("Bearer ")) {
            authorization.substring(7)
        } else {
            authorization
        }
        authService.logout(accessToken, refreshToken)
        return ResponseEntity.ok(mapOf("message" to "로그아웃 되었습니다."))
    }
}
