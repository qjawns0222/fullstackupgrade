package com.example.demo.config

import com.example.demo.service.RedisSubscriber
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

@Configuration
@org.springframework.cache.annotation.EnableCaching
class RedisConfig {

    @Bean
    fun topic(): ChannelTopic {
        return ChannelTopic("notification-topic")
    }

    @Bean
    fun messageListener(subscriber: RedisSubscriber): MessageListenerAdapter {
        return MessageListenerAdapter(subscriber)
    }

    @Bean
    fun redisMessageListener(
            connectionFactory: RedisConnectionFactory,
            listenerAdapter: MessageListenerAdapter,
            topic: ChannelTopic
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(listenerAdapter, topic)
        return container
    }

    @Bean
    @org.springframework.context.annotation.Primary
    fun cacheManager(
            connectionFactory: RedisConnectionFactory
    ): org.springframework.data.redis.cache.RedisCacheManager {
        val cacheConfig =
                org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(java.time.Duration.ofSeconds(60)) // Default TTL 60 seconds
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                org.springframework.data.redis.serializer.RedisSerializationContext
                                        .SerializationPair.fromSerializer(
                                        org.springframework.data.redis.serializer
                                                .StringRedisSerializer()
                                )
                        )
                        .serializeValuesWith(
                                org.springframework.data.redis.serializer.RedisSerializationContext
                                        .SerializationPair.fromSerializer(
                                        org.springframework.data.redis.serializer
                                                .GenericJackson2JsonRedisSerializer()
                                )
                        )

        return org.springframework.data.redis.cache.RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build()
    }
}
