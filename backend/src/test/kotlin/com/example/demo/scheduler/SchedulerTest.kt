package com.example.demo.scheduler

import com.example.demo.config.ShedLockConfig
import net.javacrumbs.shedlock.core.LockProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.redis.connection.RedisConnectionFactory

@SpringBootTest(classes = [ShedLockConfig::class])
class ShedLockConfigTest {

    @MockBean lateinit var redisConnectionFactory: RedisConnectionFactory

    @Autowired lateinit var lockProvider: LockProvider

    @Test
    fun `lockProvider bean exists`() {
        assertNotNull(lockProvider)
    }
}
