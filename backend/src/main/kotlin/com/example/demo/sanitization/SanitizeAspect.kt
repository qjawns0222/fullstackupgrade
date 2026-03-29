package com.example.demo.sanitization

import com.example.demo.annotation.Sanitize
import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody

/**
 * AOP aspect that intercepts methods annotated with [@Sanitize] and automatically
 * sanitizes all String arguments (and String fields inside @RequestBody DTOs) before
 * the method body executes.
 *
 * This prevents XSS from reaching the service layer — the sanitization happens
 * transparently at the boundary, keeping domain logic clean.
 *
 * IMPORTANT: Deep sanitization of nested DTOs uses Jackson round-trip serialization
 * only when the argument has @RequestBody on its corresponding parameter. This avoids
 * accidentally mangling binary data or non-user-facing fields.
 */
@Aspect
@Component
class SanitizeAspect(
    private val sanitizerService: HtmlSanitizerService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(sanitize)")
    fun sanitizeArguments(joinPoint: ProceedingJoinPoint, sanitize: Sanitize): Any? {
        val signature = joinPoint.signature as MethodSignature
        val parameters = signature.method.parameters
        val args = joinPoint.args

        val sanitizedArgs = args.mapIndexed { index, arg ->
            val param = parameters.getOrNull(index) ?: return@mapIndexed arg
            when {
                // Direct String argument
                arg is String -> sanitizerService.sanitizeNonNull(arg, sanitize.policy)

                // @RequestBody DTO — sanitize all String fields via Jackson reflection
                arg != null && param.isAnnotationPresent(RequestBody::class.java) ->
                    sanitizeDto(arg, sanitize)

                else -> arg
            }
        }.toTypedArray()

        return joinPoint.proceed(sanitizedArgs)
    }

    /**
     * Deep-sanitize a DTO by converting it to a Map, sanitizing all String values recursively,
     * then converting back to the original type.
     */
    @Suppress("UNCHECKED_CAST")
    private fun sanitizeDto(dto: Any, sanitize: Sanitize): Any {
        return try {
            val map = objectMapper.convertValue(dto, Map::class.java) as Map<String, Any?>
            val sanitizedMap = sanitizeMap(map, sanitize)
            objectMapper.convertValue(sanitizedMap, dto::class.java)
        } catch (e: Exception) {
            log.warn("DTO sanitization failed for type {}: {}", dto::class.simpleName, e.message)
            dto // Return original on conversion failure — do not break the request
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun sanitizeMap(map: Map<String, Any?>, sanitize: Sanitize): Map<String, Any?> {
        return map.mapValues { (_, value) ->
            when (value) {
                is String -> sanitizerService.sanitizeNonNull(value, sanitize.policy)
                is Map<*, *> -> sanitizeMap(value as Map<String, Any?>, sanitize)
                is List<*> -> value.map { item ->
                    if (item is String) sanitizerService.sanitizeNonNull(item, sanitize.policy)
                    else if (item is Map<*, *>) sanitizeMap(item as Map<String, Any?>, sanitize)
                    else item
                }
                else -> value
            }
        }
    }
}
