package com.example.demo.dto

data class MfaVerifyRequest(
        val username: String, // Or temporary token
        val otp: String // 6 digit code
)
