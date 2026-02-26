package com.example.demo.config

import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

@Configuration
class RedisBucketConfig {

    @Bean
    fun proxyManager(lettuceConnectionFactory: LettuceConnectionFactory): ProxyManager<ByteArray> {
        val client = lettuceConnectionFactory.nativeClient as RedisClient
        return LettuceBasedProxyManager.builderFor(client).build()
    }
}
