package com.example.demo.config

import com.example.demo.service.RedisSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

@Configuration
class RedisConfig {

    @Bean("notificationTopic")
    fun notificationTopic(): ChannelTopic {
        return ChannelTopic("notification-topic")
    }

    @Bean("notificationMessageListener")
    fun notificationMessageListener(subscriber: RedisSubscriber): MessageListenerAdapter {
        return MessageListenerAdapter(subscriber)
    }

    @Bean("notificationMessageListenerContainer")
    fun notificationMessageListenerContainer(
            connectionFactory: RedisConnectionFactory,
            notificationMessageListener: MessageListenerAdapter,
            notificationTopic: ChannelTopic
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(notificationMessageListener, notificationTopic)
        return container
    }

}
