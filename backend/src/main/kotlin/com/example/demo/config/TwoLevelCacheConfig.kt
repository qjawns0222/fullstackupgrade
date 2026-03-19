package com.example.demo.config

import com.example.demo.cache.CacheInvalidationPublisher
import com.example.demo.cache.CacheInvalidationSubscriber
import com.example.demo.cache.TwoLevelCacheManager
import com.example.demo.cache.TwoLevelCacheProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

/**
 * Infrastructure wiring for the two-level cache.
 *
 * Key beans:
 *  - [TwoLevelCacheManager]    → replaces the previous single-layer [RedisCacheManager]
 *  - invalidation listener     → subscribes to the Redis pub/sub channel so L1s stay fresh
 *
 * The existing [RedisConfig] pub/sub container is for notification-topic only; we register
 * a separate container here to keep concerns clean and avoid cross-contamination.
 */
@Configuration
class TwoLevelCacheConfig {

    @Bean
    @Primary
    fun twoLevelCacheManager(
        connectionFactory: RedisConnectionFactory,
        invalidationPublisher: CacheInvalidationPublisher,
        props: TwoLevelCacheProperties
    ): TwoLevelCacheManager {
        val writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory)
        return TwoLevelCacheManager(
            redisCacheWriter = writer,
            invalidationPublisher = invalidationPublisher,
            props = props
        )
    }

    @Bean("cacheInvalidationListenerAdapter")
    fun cacheInvalidationListenerAdapter(subscriber: CacheInvalidationSubscriber): MessageListenerAdapter {
        return MessageListenerAdapter(subscriber, "onMessage")
    }

    @Bean("cacheInvalidationTopic")
    fun cacheInvalidationTopic(props: TwoLevelCacheProperties): ChannelTopic {
        return ChannelTopic(props.invalidationTopic)
    }

    /**
     * Dedicated listener container for cache invalidation events.
     * Separate from the notification-topic container in [RedisConfig].
     */
    @Bean("cacheInvalidationListenerContainer")
    fun cacheInvalidationListenerContainer(
        connectionFactory: RedisConnectionFactory,
        cacheInvalidationListenerAdapter: MessageListenerAdapter,
        cacheInvalidationTopic: ChannelTopic
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(cacheInvalidationListenerAdapter, cacheInvalidationTopic)
        return container
    }
}
