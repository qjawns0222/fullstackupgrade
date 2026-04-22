package com.example.demo.baggage

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskDecorator

class BaggageTaskDecorator(private val tracer: Tracer) : TaskDecorator {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun decorate(runnable: Runnable): Runnable {
        val userId = tracer.getBaggage(BaggageContextHolder.USER_ID_KEY)?.get()
        val tenantId = tracer.getBaggage(BaggageContextHolder.TENANT_ID_KEY)?.get()
        val mdcContext = org.slf4j.MDC.getCopyOfContextMap()

        return Runnable {
            try {
                if (mdcContext != null) org.slf4j.MDC.setContextMap(mdcContext)
                userId?.let {
                    tracer.createBaggage(BaggageContextHolder.USER_ID_KEY, it).makeCurrent(it)
                }
                tenantId?.let {
                    tracer.createBaggage(BaggageContextHolder.TENANT_ID_KEY, it).makeCurrent(it)
                }
                log.debug("[BaggageTaskDecorator] restored userId={} tenantId={}", userId, tenantId)
                runnable.run()
            } finally {
                org.slf4j.MDC.clear()
            }
        }
    }
}
