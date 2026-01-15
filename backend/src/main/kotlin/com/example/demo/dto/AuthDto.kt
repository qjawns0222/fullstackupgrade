package com.example.demo.dto

data class LoginRequest(val username: String, val password: String)

data class TokenDto(
        val grantType: String,
        val accessToken: String,
        val refreshToken: String,
        val accessTokenExpiresIn: Long
)
