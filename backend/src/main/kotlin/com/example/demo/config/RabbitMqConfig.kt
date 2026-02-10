package com.example.demo.config

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
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
    }

    @Bean
    fun auditQueue(): Queue {
        return Queue(AUDIT_QUEUE, true)
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
        return template
    }
}
