package com.example.demo.baggage

import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.stereotype.Component

@Component
class BaggageMessagePostProcessor(
    private val baggageContextHolder: BaggageContextHolder
) : MessagePostProcessor {

    @Throws(AmqpException::class)
    override fun postProcessMessage(message: Message): Message {
        val ctx = baggageContextHolder.get()
        ctx.userId?.let { message.messageProperties.setHeader(BaggageContextHolder.USER_ID_KEY, it) }
        ctx.tenantId?.let { message.messageProperties.setHeader(BaggageContextHolder.TENANT_ID_KEY, it) }
        return message
    }
}
