package com.example.demo.tracing

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class WithSpanAspectTest {

    private lateinit var spanStore: FakeSpanStore
    private lateinit var aspect: WithSpanAspect

    @BeforeEach
    fun setUp() {
        spanStore = FakeSpanStore()
        aspect = WithSpanAspect(spanStore)
    }

    @Test
    fun `success span is saved with SUCCESS status`() {
        val result = aspect.trace(joinPoint("TestService", "doWork") { "ok" }, annotation())

        assertEquals("ok", result)
        val record = spanStore.saved.single()
        assertEquals("SUCCESS", record.status)
        assertNull(record.errorMessage)
        assertEquals("TestService.doWork", record.spanName)
    }

    @Test
    fun `slow span is saved with SLOW status`() {
        aspect.trace(
            joinPoint("SlowService", "heavyMethod") { Thread.sleep(10); "done" },
            annotation(slowThresholdMs = 1L)
        )
        assertEquals("SLOW", spanStore.saved.single().status)
    }

    @Test
    fun `error span is saved with ERROR status and rethrows`() {
        assertThrows(RuntimeException::class.java) {
            aspect.trace(
                joinPoint("ErrService", "boom") { throw RuntimeException("kaboom") },
                annotation()
            )
        }
        val record = spanStore.saved.single()
        assertEquals("ERROR", record.status)
        assertEquals("kaboom", record.errorMessage)
    }

    @Test
    fun `custom span name overrides default`() {
        aspect.trace(joinPoint("A", "b") { Unit }, annotation(name = "my-span"))
        assertEquals("my-span", spanStore.saved.single().spanName)
    }

    // ---- helpers ----

    private fun annotation(name: String = "", slowThresholdMs: Long = 500L): WithSpan {
        val ann = mock(WithSpan::class.java)
        `when`(ann.name).thenReturn(name)
        `when`(ann.slowThresholdMs).thenReturn(slowThresholdMs)
        return ann
    }

    private fun joinPoint(className: String, methodName: String, proceed: () -> Any?): ProceedingJoinPoint {
        val sig = mock(MethodSignature::class.java)
        `when`(sig.declaringType).thenReturn(String::class.java)
        `when`(sig.declaringTypeName).thenReturn(className)
        `when`(sig.name).thenReturn(methodName)
        `when`(sig.method).thenReturn(String::class.java.getMethod("toString"))

        val jp = mock(ProceedingJoinPoint::class.java)
        `when`(jp.signature).thenReturn(sig)
        `when`(jp.args).thenReturn(emptyArray())
        `when`(jp.proceed()).thenAnswer { proceed() }
        return jp
    }
}

class FakeSpanStore : SpanStore {
    val saved = mutableListOf<SpanRecord>()

    override fun save(record: SpanRecord) = record.also { saved.add(it) }
    override fun findRecent(limit: Int) = saved.takeLast(limit)
    override fun findSlowSpans(thresholdMs: Long, limit: Int) =
        saved.filter { it.durationMs >= thresholdMs }.take(limit)
    override fun stats() = SpanStats(
        totalCount = saved.size.toLong(),
        slowCount = saved.count { it.status == "SLOW" }.toLong(),
        errorCount = saved.count { it.status == "ERROR" }.toLong(),
        avgDurationMs = if (saved.isEmpty()) 0.0 else saved.map { it.durationMs }.average()
    )
}
