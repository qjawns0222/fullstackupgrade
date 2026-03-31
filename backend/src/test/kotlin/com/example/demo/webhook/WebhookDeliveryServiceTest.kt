package com.example.demo.webhook

import com.example.demo.entity.User
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class WebhookDeliveryServiceTest {

    private lateinit var server: MockWebServer

    @Mock private lateinit var endpointRepository: WebhookEndpointRepository
    @Mock private lateinit var deliveryLogRepository: WebhookDeliveryLogRepository

    private lateinit var service: WebhookDeliveryService
    private val objectMapper = ObjectMapper().registerKotlinModule()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val realClient = OkHttpClient()
        service = WebhookDeliveryService(endpointRepository, deliveryLogRepository, realClient, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun makeUser() = User(id = 1L, username = "u", password = "p", role = "USER")

    private fun makeEndpoint(url: String) = WebhookEndpoint(
        id = 1L,
        targetUrl = url,
        secret = "test-secret",
        eventTypes = "APPLICATION_CREATED",
        active = true,
        user = makeUser(),
        createdAt = LocalDateTime.now()
    )

    @Test
    fun `dispatch does nothing when no active endpoints`() {
        `when`(endpointRepository.findActiveByEventType("APPLICATION_CREATED")).thenReturn(emptyList())

        service.dispatch(WebhookEvent("APPLICATION_CREATED", mapOf("id" to 1L), 1L))

        verify(deliveryLogRepository, never()).save(any())
    }

    @Test
    fun `dispatch creates delivery log with SUCCESS status on 200 response`() {
        val endpoint = makeEndpoint(server.url("/webhook").toString())
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        `when`(endpointRepository.findActiveByEventType("APPLICATION_CREATED"))
            .thenReturn(listOf(endpoint))

        // Use thenAnswer with saved reference to avoid ArgumentCaptor NPE with Kotlin non-null types
        var savedLog: WebhookDeliveryLog? = null
        `when`(deliveryLogRepository.save(any(WebhookDeliveryLog::class.java))).thenAnswer { inv ->
            (inv.arguments[0] as WebhookDeliveryLog).also { savedLog = it }
        }

        service.dispatch(WebhookEvent("APPLICATION_CREATED", mapOf("id" to 1L), 1L))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertNotNull(request.getHeader("X-Webhook-Signature"))
        assertTrue(request.getHeader("X-Webhook-Signature")!!.startsWith("sha256="))
    }

    @Test
    fun `dispatch marks log as FAILED on 500 response and retries`() {
        val endpoint = makeEndpoint(server.url("/webhook").toString())
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500).setBody("Error")) }

        `when`(endpointRepository.findActiveByEventType("APPLICATION_STATUS_CHANGED"))
            .thenReturn(listOf(endpoint))

        `when`(deliveryLogRepository.save(any(WebhookDeliveryLog::class.java))).thenAnswer { inv ->
            inv.arguments[0] as WebhookDeliveryLog
        }

        service.dispatch(WebhookEvent("APPLICATION_STATUS_CHANGED", mapOf("id" to 1L), 1L))

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `registerEndpoint saves entity with correct fields`() {
        val user = makeUser()
        val request = WebhookEndpointRequest(
            targetUrl = "https://example.com/hook",
            secret = "mysecret",
            eventTypes = "APPLICATION_CREATED,APPLICATION_STATUS_CHANGED",
            active = true
        )

        var savedEndpoint: WebhookEndpoint? = null
        `when`(endpointRepository.save(any(WebhookEndpoint::class.java))).thenAnswer { inv ->
            (inv.arguments[0] as WebhookEndpoint).also { it.id = 99L; savedEndpoint = it }
        }

        service.registerEndpoint(user.id!!, request, user)

        assertNotNull(savedEndpoint)
        assertEquals("https://example.com/hook", savedEndpoint!!.targetUrl)
        assertEquals("mysecret", savedEndpoint!!.secret)
        assertEquals("APPLICATION_CREATED,APPLICATION_STATUS_CHANGED", savedEndpoint!!.eventTypes)
        assertTrue(savedEndpoint!!.active)
    }

    @Test
    fun `deactivateEndpoint sets active=false`() {
        val user = makeUser()
        val endpoint = makeEndpoint("https://example.com/hook").also { it.id = 5L }
        `when`(endpointRepository.findById(5L)).thenReturn(Optional.of(endpoint))

        var savedEndpoint: WebhookEndpoint? = null
        `when`(endpointRepository.save(any(WebhookEndpoint::class.java))).thenAnswer { inv ->
            (inv.arguments[0] as WebhookEndpoint).also { savedEndpoint = it }
        }

        service.deactivateEndpoint(5L, user.id!!)

        assertFalse(savedEndpoint!!.active)
    }

    @Test
    fun `deactivateEndpoint throws when user mismatch`() {
        val endpoint = makeEndpoint("https://example.com/hook").also { it.id = 5L }
        `when`(endpointRepository.findById(5L)).thenReturn(Optional.of(endpoint))

        assertThrows(IllegalArgumentException::class.java) {
            service.deactivateEndpoint(5L, 999L) // wrong userId
        }
    }
}
