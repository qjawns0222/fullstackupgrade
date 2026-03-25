package com.example.demo.validation

import com.example.demo.annotation.ValidateJsonSchema
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.bind.annotation.RequestBody
import java.lang.reflect.Method
import java.lang.reflect.Parameter

/**
 * Helper object providing real annotation instances via reflection.
 */
object AnnotationHolder {
    @ValidateJsonSchema(schemaPath = "schemas/job-application.json")
    fun annotatedMethod() {}

    fun getAnnotation(): ValidateJsonSchema =
        this::class.java.getMethod("annotatedMethod").getAnnotation(ValidateJsonSchema::class.java)
}

@ExtendWith(MockitoExtension::class)
class JsonSchemaValidationAspectTest {

    private lateinit var schemaRegistry: JsonSchemaRegistry
    private lateinit var violationStore: SchemaViolationStore
    private lateinit var objectMapper: ObjectMapper
    private lateinit var aspect: JsonSchemaValidationAspect

    @Mock private lateinit var joinPoint: ProceedingJoinPoint
    @Mock private lateinit var signature: MethodSignature
    @Mock private lateinit var method: Method
    @Mock private lateinit var param: Parameter

    private lateinit var validateAnnotation: ValidateJsonSchema

    @BeforeEach
    fun setUp() {
        schemaRegistry = JsonSchemaRegistry()
        violationStore = SchemaViolationStore()
        objectMapper = ObjectMapper().registerModule(JavaTimeModule())
        aspect = JsonSchemaValidationAspect(schemaRegistry, violationStore, objectMapper)
        validateAnnotation = AnnotationHolder.getAnnotation()

        `when`(param.isAnnotationPresent(RequestBody::class.java)).thenReturn(true)
        `when`(method.parameters).thenReturn(arrayOf(param))
        `when`(signature.method).thenReturn(method)
        `when`(joinPoint.signature).thenReturn(signature)
    }

    @Test
    fun `valid payload passes through without exception`() {
        val payload = mapOf(
            "companyName" to "ACME Corp",
            "position" to "Backend Engineer",
            "status" to "APPLIED",
            "appliedDate" to "2026-03-25"
        )
        `when`(joinPoint.args).thenReturn(arrayOf(payload))
        `when`(joinPoint.proceed()).thenReturn(Unit)

        assertDoesNotThrow {
            aspect.validate(joinPoint, validateAnnotation)
        }
        verify(joinPoint).proceed()
    }

    @Test
    fun `missing required field throws SchemaValidationException`() {
        val payload = mapOf(
            "position" to "Backend Engineer",
            "status" to "APPLIED",
            "appliedDate" to "2026-03-25"
            // companyName missing
        )
        `when`(joinPoint.args).thenReturn(arrayOf(payload))

        val ex = assertThrows(SchemaValidationException::class.java) {
            aspect.validate(joinPoint, validateAnnotation)
        }
        assertTrue(ex.violations.isNotEmpty())
        assertEquals("schemas/job-application.json", ex.schemaPath)
        verify(joinPoint, never()).proceed()
    }

    @Test
    fun `violation is recorded to store when validation fails`() {
        val payload = mapOf(
            "companyName" to "ACME",
            "position" to "Dev",
            "status" to "INVALID_STATUS",
            "appliedDate" to "2026-03-25"
        )
        `when`(joinPoint.args).thenReturn(arrayOf(payload))

        assertThrows(SchemaValidationException::class.java) {
            aspect.validate(joinPoint, validateAnnotation)
        }

        val violations = violationStore.getRecent(10)
        assertEquals(1, violations.size)
        assertEquals("schemas/job-application.json", violations[0].schemaPath)
    }

    @Test
    fun `no RequestBody param skips validation and proceeds`() {
        // Override setUp stubs: method has no @RequestBody-annotated params
        `when`(param.isAnnotationPresent(RequestBody::class.java)).thenReturn(false)
        val payload = mapOf("companyName" to "Test")
        `when`(joinPoint.args).thenReturn(arrayOf(payload))
        `when`(joinPoint.proceed()).thenReturn(Unit)

        assertDoesNotThrow { aspect.validate(joinPoint, validateAnnotation) }
        verify(joinPoint).proceed()
    }

    @Test
    fun `additional properties are rejected`() {
        val payload = mapOf(
            "companyName" to "ACME Corp",
            "position" to "Backend Engineer",
            "status" to "APPLIED",
            "appliedDate" to "2026-03-25",
            "unknownField" to "shouldFail"
        )
        `when`(joinPoint.args).thenReturn(arrayOf(payload))

        val ex = assertThrows(SchemaValidationException::class.java) {
            aspect.validate(joinPoint, validateAnnotation)
        }
        assertTrue(ex.violations.isNotEmpty())
    }

    @Test
    fun `interview status without memo fails validation`() {
        val payload = mapOf(
            "companyName" to "ACME Corp",
            "position" to "Backend Engineer",
            "status" to "INTERVIEW",
            "appliedDate" to "2026-03-25"
            // memo missing but required when INTERVIEW
        )
        `when`(joinPoint.args).thenReturn(arrayOf(payload))

        val ex = assertThrows(SchemaValidationException::class.java) {
            aspect.validate(joinPoint, validateAnnotation)
        }
        assertTrue(ex.violations.isNotEmpty())
    }

    @Test
    fun `interview status with memo passes validation`() {
        val payload = mapOf(
            "companyName" to "ACME Corp",
            "position" to "Backend Engineer",
            "status" to "INTERVIEW",
            "appliedDate" to "2026-03-25",
            "memo" to "First round technical interview"
        )
        `when`(joinPoint.args).thenReturn(arrayOf(payload))
        `when`(joinPoint.proceed()).thenReturn(Unit)

        assertDoesNotThrow {
            aspect.validate(joinPoint, validateAnnotation)
        }
    }
}
