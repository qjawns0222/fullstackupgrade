package com.example.demo.validation

import com.example.demo.annotation.ValidateJsonSchema
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

@Aspect
@Component
class JsonSchemaValidationAspect(
    private val schemaRegistry: SchemaRegistry,
    private val violationStore: ViolationStore,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(validateJsonSchema)")
    fun validate(joinPoint: ProceedingJoinPoint, validateJsonSchema: ValidateJsonSchema): Any? {
        val schemaPath = validateJsonSchema.schemaPath
        val requestBody = resolveRequestBody(joinPoint)

        if (requestBody != null) {
            val schema = schemaRegistry.getSchema(schemaPath)
            val jsonNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(requestBody)
            val errors = schema.validate(jsonNode)

            if (errors.isNotEmpty()) {
                val messages = errors.map { it.message }
                val (endpoint, method) = resolveRequestInfo()
                val payload = runCatching { objectMapper.writeValueAsString(requestBody) }.getOrDefault("{}")

                val violation = SchemaViolation(
                    id = UUID.randomUUID().toString(),
                    schemaPath = schemaPath,
                    endpoint = endpoint,
                    method = method,
                    violations = messages,
                    requestPayload = payload
                )

                violationStore.record(violation)
                log.warn("JSON Schema validation failed [schema={}] violations={}", schemaPath, messages)
                throw SchemaValidationException(messages, schemaPath)
            }
        }

        return joinPoint.proceed()
    }

    private fun resolveRequestBody(joinPoint: ProceedingJoinPoint): Any? {
        val signature = joinPoint.signature as MethodSignature
        val params = signature.method.parameters
        val args = joinPoint.args

        return params.indices
            .firstOrNull { params[it].isAnnotationPresent(RequestBody::class.java) }
            ?.let { args[it] }
    }

    private fun resolveRequestInfo(): Pair<String, String> {
        val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        val request: HttpServletRequest? = attrs?.request
        return Pair(request?.requestURI ?: "unknown", request?.method ?: "unknown")
    }
}
