package com.example.demo.aop

import com.example.demo.annotation.AuditLog
import com.example.demo.document.AuditLogDocument
import com.example.demo.repository.AuditLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Aspect
@Component
class AuditLogAspect(
        private val auditLogRepository: AuditLogRepository,
        private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(AuditLogAspect::class.java)

    @Around("@annotation(auditLog)")
    fun handleAuditLog(joinPoint: ProceedingJoinPoint, auditLog: AuditLog): Any? {
        val signature = joinPoint.signature as MethodSignature
        val methodName = signature.method.name
        val args = joinPoint.args

        // Get current user (simple implementation relying on SecurityContext)
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = authentication?.name ?: "ANONYMOUS"

        val paramsJson =
                try {
                    objectMapper.writeValueAsString(args)
                } catch (e: Exception) {
                    "Error serializing params"
                }

        var status = "SUCCESS"
        var errorMessage: String? = null
        var result: Any? = null

        try {
            result = joinPoint.proceed()
            return result
        } catch (e: Exception) {
            status = "FAILURE"
            errorMessage = e.message
            throw e
        } finally {
            // Async logging to avoid blocking the main thread
            CompletableFuture.runAsync {
                try {
                    val logDoc =
                            AuditLogDocument(
                                    userId = userId,
                                    action = auditLog.action.ifBlank { methodName },
                                    description = auditLog.description,
                                    params = paramsJson,
                                    status = status,
                                    errorMessage = errorMessage,
                                    timestamp = LocalDateTime.now()
                            )
                    auditLogRepository.save(logDoc)
                    logger.info("Audit log saved: $logDoc")
                } catch (e: Exception) {
                    logger.error("Failed to save audit log", e)
                }
            }
        }
    }
}
