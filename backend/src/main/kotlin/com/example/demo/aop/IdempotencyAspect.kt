package com.example.demo.aop

import com.example.demo.annotation.Idempotent
import com.example.demo.exception.IdempotencyException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class IdempotencyAspect(private val redisTemplate: StringRedisTemplate) {

    @Around("@annotation(idempotent)")
    fun handleIdempotency(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        val requestAttributes =
                RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        val request =
                requestAttributes?.request
                        ?: throw IllegalStateException("Request context not found")

        val keyHeader = idempotent.keyHeader
        val idempotencyKey = request.getHeader(keyHeader)

        if (idempotencyKey.isNullOrBlank()) {
            // If the client doesn't provide a key, we might choose to generate one or skip.
            // But for strict idempotency enforcement, we should require it.
            throw IdempotencyException("Idempotency key is missing in header: $keyHeader")
        }

        val redisKey = "idempotency:$idempotencyKey"

        // Check if key exists
        val isDuplicate = redisTemplate.hasKey(redisKey)
        if (isDuplicate) {
            throw IdempotencyException("Duplicate request detected for key: $idempotencyKey")
        }

        // Set key with TTL
        redisTemplate
                .opsForValue()
                .set(
                        redisKey,
                        "PROCESSING", // Or some status
                        idempotent.expireTime,
                        idempotent.timeUnit
                )

        try {
            return joinPoint.proceed()
        } catch (e: Exception) {
            // Ideally if the process fails, we might want to allow retry?
            // Depends on the policy. For this missions, let's keep it simple:
            // if it failed, maybe we remove the key so they can try again?
            // "Self Heal" -> logic. I'll stick to "Fail-Safe".
            // If processing failed, we remove the key to allow retry.
            redisTemplate.delete(redisKey)
            throw e
        }
    }
}
