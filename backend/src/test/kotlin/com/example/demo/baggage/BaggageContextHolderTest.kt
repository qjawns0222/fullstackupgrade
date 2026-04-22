package com.example.demo.baggage

import io.micrometer.tracing.Baggage
import io.micrometer.tracing.BaggageInScope
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BaggageContextHolderTest {

    private lateinit var holder: BaggageContextHolder
    private lateinit var tracer: FakeTracer

    @BeforeEach
    fun setUp() {
        tracer = FakeTracer()
        holder = BaggageContextHolder(tracer)
    }

    @Test
    fun `set stores userId and tenantId in baggage`() {
        holder.set(userId = "user-1", tenantId = "tenant-1")

        val ctx = holder.get()
        assertEquals("user-1", ctx.userId)
        assertEquals("tenant-1", ctx.tenantId)
    }

    @Test
    fun `get returns empty context when no baggage set`() {
        val ctx = holder.get()
        assertNull(ctx.userId)
        assertNull(ctx.tenantId)
        assertFalse(ctx.isPresent())
    }

    @Test
    fun `set with only userId leaves tenantId null`() {
        holder.set(userId = "user-42", tenantId = null)

        val ctx = holder.get()
        assertEquals("user-42", ctx.userId)
        assertNull(ctx.tenantId)
        assertTrue(ctx.isPresent())
    }

    @Test
    fun `snapshot returns only userId and tenantId keys`() {
        holder.set(userId = "u1", tenantId = "t1")

        val snap = holder.snapshot()
        assertEquals("u1", snap[BaggageContextHolder.USER_ID_KEY])
        assertEquals("t1", snap[BaggageContextHolder.TENANT_ID_KEY])
        assertTrue(snap.keys.all { it == BaggageContextHolder.USER_ID_KEY || it == BaggageContextHolder.TENANT_ID_KEY })
    }

    @Test
    fun `BaggageContext EMPTY has no values`() {
        assertFalse(BaggageContext.EMPTY.isPresent())
        assertNull(BaggageContext.EMPTY.userId)
        assertNull(BaggageContext.EMPTY.tenantId)
    }

    // --- Fake Tracer ---

    class FakeTracer : Tracer {
        private val store = mutableMapOf<String, String>()

        override fun createBaggage(name: String): Baggage = FakeBaggage(name, null, store)
        override fun createBaggage(name: String, value: String): Baggage = FakeBaggage(name, value, store).also { store[name] = value }
        override fun getBaggage(name: String): Baggage? = FakeBaggage(name, store[name], store)
        override fun getBaggage(context: io.micrometer.tracing.TraceContext, name: String): Baggage? = getBaggage(name)
        override fun getAllBaggage(): Map<String, String> = store.toMap()
        override fun getAllBaggage(context: io.micrometer.tracing.TraceContext?): Map<String, String> = getAllBaggage()

        override fun currentSpan(): io.micrometer.tracing.Span? = null
        override fun nextSpan(): io.micrometer.tracing.Span = io.micrometer.tracing.Span.NOOP
        override fun nextSpan(parent: io.micrometer.tracing.Span?): io.micrometer.tracing.Span = io.micrometer.tracing.Span.NOOP
        override fun spanBuilder(): io.micrometer.tracing.Span.Builder = io.micrometer.tracing.Span.Builder.NOOP
        override fun traceContextBuilder(): io.micrometer.tracing.TraceContext.Builder = io.micrometer.tracing.TraceContext.Builder.NOOP
        override fun startScopedSpan(name: String): io.micrometer.tracing.ScopedSpan = io.micrometer.tracing.ScopedSpan.NOOP
        override fun currentTraceContext(): io.micrometer.tracing.CurrentTraceContext = io.micrometer.tracing.CurrentTraceContext.NOOP
        override fun currentSpanCustomizer(): io.micrometer.tracing.SpanCustomizer = io.micrometer.tracing.SpanCustomizer.NOOP
        override fun withSpan(span: io.micrometer.tracing.Span?): Tracer.SpanInScope = Tracer.SpanInScope { }
    }

    class FakeBaggage(
        private val name: String,
        private val currentValue: String?,
        private val store: MutableMap<String, String>
    ) : Baggage {
        override fun name(): String = name
        override fun get(): String? = currentValue ?: store[name]
        override fun get(context: io.micrometer.tracing.TraceContext): String? = get()
        override fun set(value: String): Baggage = FakeBaggage(name, value, store).also { store[name] = value }
        override fun set(context: io.micrometer.tracing.TraceContext, value: String): Baggage = set(value)
        override fun makeCurrent(): BaggageInScope = BaggageInScope.NOOP
        override fun makeCurrent(value: String): BaggageInScope {
            store[name] = value
            return BaggageInScope.NOOP
        }
        override fun makeCurrent(context: io.micrometer.tracing.TraceContext, value: String): BaggageInScope = makeCurrent(value)
    }
}
