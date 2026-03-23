package com.example.demo.webhook

import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class WebhookConfig {

    /**
     * Dedicated OkHttpClient for webhook outbound calls.
     * 10-second connect/read timeouts prevent a slow external endpoint
     * from stalling the delivery thread indefinitely.
     */
    @Bean
    fun okHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Isolated thread pool for async webhook delivery.
     * Separate from the mail executor so webhook retries cannot
     * starve other async operations.
     */
    @Bean(name = ["webhookExecutor"])
    fun webhookExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 4
        executor.maxPoolSize = 20
        executor.queueCapacity = 200
        executor.setThreadNamePrefix("Webhook-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }
}
