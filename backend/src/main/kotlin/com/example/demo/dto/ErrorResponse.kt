package com.example.demo.dto

data class ErrorResponse(val code: String, val message: String, val detail: String? = null)
