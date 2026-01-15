package com.example.demo.entity

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash(value = "refreshToken", timeToLive = 604800) // 7 days
data class RefreshToken(@Id val refreshToken: String, val username: String)
