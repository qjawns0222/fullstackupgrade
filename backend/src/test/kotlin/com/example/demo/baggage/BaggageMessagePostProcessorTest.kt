package com.example.demo.baggage

import io.micrometer.tracing.Baggage
import io.micrometer.tracing.BaggageInScope
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties

class BaggageMessagePostProcessorTest {

    @Test
    fun `postProcessMessage injects userId and tenantId headers`() {
        val tracer = BaggageContextHolderTest.FakeTracer()
        val holder = BaggageContextHolder(tracer)
        holder.set(userId = "user-99", tenantId = "tenant-A")

        val processor = BaggageMessagePostProcessor(holder)
        val props = MessageProperties()
        val message = Message(ByteArray(0), props)

        val result = processor.postProcessMessage(message)

        assertEquals("user-99", result.messageProperties.getHeader(BaggageContextHolder.USER_ID_KEY))
        assertEquals("tenant-A", result.messageProperties.getHeader(BaggageContextHolder.TENANT_ID_KEY))
    }

    @Test
    fun `postProcessMessage skips null values`() {
        val tracer = BaggageContextHolderTest.FakeTracer()
        val holder = BaggageContextHolder(tracer)
        holder.set(userId = "user-1", tenantId = null)

        val processor = BaggageMessagePostProcessor(holder)
        val props = MessageProperties()
        val message = Message(ByteArray(0), props)

        val result = processor.postProcessMessage(message)

        assertEquals("user-1", result.messageProperties.getHeader(BaggageContextHolder.USER_ID_KEY))
        assertNull(result.messageProperties.getHeader<String>(BaggageContextHolder.TENANT_ID_KEY))
    }
}
