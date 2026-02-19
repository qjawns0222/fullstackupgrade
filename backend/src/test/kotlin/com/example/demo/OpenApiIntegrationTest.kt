package com.example.demo

import com.example.demo.repository.AuditLogRepository
import com.example.demo.repository.ResumeSearchRepository
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Disabled("Environment setup is too complex for integration test currently")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockBean private lateinit var auditLogRepository: AuditLogRepository

    @MockBean private lateinit var resumeSearchRepository: ResumeSearchRepository

    @MockBean
    private lateinit var redisConnectionFactory:
            org.springframework.data.redis.connection.RedisConnectionFactory

    @MockBean
    private lateinit var redisMessageListenerContainer:
            org.springframework.data.redis.listener.RedisMessageListenerContainer

    @Test
    fun `should return openapi docs`() {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk)
    }
}
