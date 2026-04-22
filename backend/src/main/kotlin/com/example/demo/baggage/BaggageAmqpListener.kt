package com.example.demo.baggage

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.stereotype.Component

@Component
class BaggageAmqpListener(private val tracer: Tracer) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun restoreBaggageFromMessage(message: Message) {
        val props = message.messageProperties
        val userId = props.getHeader<String>(BaggageContextHolder.USER_ID_KEY)
        val tenantId = props.getHeader<String>(BaggageContextHolder.TENANT_ID_KEY)

        userId?.let {
            tracer.createBaggage(BaggageContextHolder.USER_ID_KEY, it).makeCurrent(it)
        }
        tenantId?.let {
            tracer.createBaggage(BaggageContextHolder.TENANT_ID_KEY, it).makeCurrent(it)
        }
        log.debug("[BaggageAmqpListener] restored from AMQP headers userId={} tenantId={}", userId, tenantId)
    }
}
