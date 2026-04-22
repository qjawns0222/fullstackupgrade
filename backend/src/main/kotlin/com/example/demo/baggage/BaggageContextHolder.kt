package com.example.demo.baggage

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BaggageContextHolder(private val tracer: Tracer) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun set(userId: String?, tenantId: String?) {
        userId?.let {
            val baggage = tracer.createBaggage(USER_ID_KEY, it)
            baggage.makeCurrent(it)
        }
        tenantId?.let {
            val baggage = tracer.createBaggage(TENANT_ID_KEY, it)
            baggage.makeCurrent(it)
        }
        log.debug("[Baggage] set userId={} tenantId={}", userId, tenantId)
    }

    fun get(): BaggageContext {
        val userId = tracer.getBaggage(USER_ID_KEY)?.get()
        val tenantId = tracer.getBaggage(TENANT_ID_KEY)?.get()
        return BaggageContext(userId = userId, tenantId = tenantId)
    }

    fun snapshot(): Map<String, String> {
        return tracer.getAllBaggage().filterKeys { it == USER_ID_KEY || it == TENANT_ID_KEY }
    }

    companion object {
        const val USER_ID_KEY = "userId"
        const val TENANT_ID_KEY = "tenantId"
    }
}
