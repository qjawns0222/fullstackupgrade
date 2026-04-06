package com.example.demo.config

import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMqConfig {

    companion object {
        const val AUDIT_QUEUE = "audit.queue"
        const val AUDIT_EXCHANGE = "audit.exchange"
        const val AUDIT_ROUTING_KEY = "audit.routing.key"

        const val AUDIT_DLQ = "audit.dlq"
        const val AUDIT_DLX = "audit.dlx"
        const val AUDIT_DLQ_ROUTING_KEY = "audit.dlq.routing.key"
    }

    @Bean
    fun auditDlx(): DirectExchange {
        return DirectExchange(AUDIT_DLX)
    }

    @Bean
    fun auditDlq(): Queue {
        return QueueBuilder.durable(AUDIT_DLQ).build()
    }

    @Bean
    fun auditDlqBinding(auditDlq: Queue, auditDlx: DirectExchange): Binding {
        return BindingBuilder.bind(auditDlq).to(auditDlx).with(AUDIT_DLQ_ROUTING_KEY)
    }

    @Bean
    fun auditQueue(): Queue {
        return QueueBuilder.durable(AUDIT_QUEUE)
            .withArgument("x-dead-letter-exchange", AUDIT_DLX)
            .withArgument("x-dead-letter-routing-key", AUDIT_DLQ_ROUTING_KEY)
            .withArgument("x-message-ttl", 60000) // 60초 TTL 초과 시 DLQ로
            .build()
    }

    @Bean
    fun auditExchange(): TopicExchange {
        return TopicExchange(AUDIT_EXCHANGE)
    }

    @Bean
    fun binding(auditQueue: Queue, auditExchange: TopicExchange): Binding {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(AUDIT_ROUTING_KEY)
    }

    @Bean
    fun messageConverter(): MessageConverter {
        return Jackson2JsonMessageConverter()
    }

    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = messageConverter()
        template.setObservationEnabled(true)
        return template
    }
}
