package com.example.demo.dto

import com.example.demo.exception.ErrorCode

data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: String? = null
) {
    companion object {
        fun of(errorCode: ErrorCode): ErrorResponse {
            return ErrorResponse(
                code = errorCode.code,
                message = errorCode.message
            )
        }

        fun of(errorCode: ErrorCode, detail: String): ErrorResponse {
            return ErrorResponse(
                code = errorCode.code,
                message = errorCode.message,
                detail = detail
            )
        }
    }
}
