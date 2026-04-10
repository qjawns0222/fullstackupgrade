package com.example.demo.tracing

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class WithSpanAspect(private val spanStore: SpanStore) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(withSpan)")
    fun trace(joinPoint: ProceedingJoinPoint, withSpan: WithSpan): Any? {
        val signature = joinPoint.signature as MethodSignature
        val className = signature.declaringTypeName.substringAfterLast('.')
        val methodName = signature.name
        val spanName = withSpan.name.ifBlank { "$className.$methodName" }

        val startMs = System.currentTimeMillis()
        var status = "SUCCESS"
        var errorMessage: String? = null

        return try {
            val result = joinPoint.proceed()
            val durationMs = System.currentTimeMillis() - startMs
            if (durationMs >= withSpan.slowThresholdMs) {
                status = "SLOW"
                logger.warn("[WithSpan] SLOW span detected: {} took {}ms (threshold={}ms)", spanName, durationMs, withSpan.slowThresholdMs)
            }
            result
        } catch (ex: Throwable) {
            status = "ERROR"
            errorMessage = ex.message?.take(500)
            throw ex
        } finally {
            val durationMs = System.currentTimeMillis() - startMs
            try {
                spanStore.save(
                    SpanRecord(
                        spanName = spanName,
                        className = className,
                        methodName = methodName,
                        durationMs = durationMs,
                        status = status,
                        errorMessage = errorMessage
                    )
                )
            } catch (ex: Exception) {
                logger.error("[WithSpan] Failed to save span record for {}", spanName, ex)
            }
        }
    }
}
