package com.example.demo.exception

class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
