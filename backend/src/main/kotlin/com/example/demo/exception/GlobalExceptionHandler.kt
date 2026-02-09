package com.example.demo.exception

import com.example.demo.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** Business Logic Exception */
    @ExceptionHandler(BusinessException::class)
    protected fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        log.error("handleBusinessException", e)
        val errorCode = e.errorCode
        val response = ErrorResponse(code = errorCode.code, message = errorCode.message)
        return ResponseEntity(response, errorCode.status)
    }

    /** Idempotency Exception */
    @ExceptionHandler(IdempotencyException::class)
    protected fun handleIdempotencyException(
            e: IdempotencyException
    ): ResponseEntity<ErrorResponse> {
        log.error("handleIdempotencyException", e)
        val errorCode = ErrorCode.DUPLICATE_REQUEST
        val response =
                ErrorResponse(
                        code = errorCode.code,
                        message = errorCode.message,
                        detail = e.message ?: errorCode.message
                )
        return ResponseEntity(response, errorCode.status)
    }

    /** Generic Exception */
    @ExceptionHandler(Exception::class)
    protected fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("handleException", e)
        val errorCode = ErrorCode.INTERNAL_SERVER_ERROR
        val response =
                ErrorResponse(
                        code = errorCode.code,
                        message = errorCode.message,
                        detail = e.message ?: "Unknown Error"
                )
        return ResponseEntity(response, errorCode.status)
    }
}
