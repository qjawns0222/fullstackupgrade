package com.example.demo.notification

import com.example.demo.entity.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationRouterTest {

    private lateinit var store: FakeNotificationPreferenceStore
    private lateinit var dispatcher: FakeNotificationDispatcher
    private lateinit var router: NotificationRouter

    private val user = User(id = 42L, username = "tester", password = "p", role = "ROLE_USER")
    private val event = NotificationEvent("TEST", "Hello", "world")

    @BeforeEach
    fun setUp() {
        store = FakeNotificationPreferenceStore()
        dispatcher = FakeNotificationDispatcher()
        router = NotificationRouter(store, dispatcher)
    }

    @Test
    fun `defaults to STOMP when no preferences saved`() {
        router.route(user.id!!, event)
        assertEquals(1, dispatcher.stomps)
        assertEquals(0, dispatcher.webhooks)
    }

    @Test
    fun `routes only to enabled channels`() {
        store.addPref(user, NotificationChannel.STOMP, enabled = true)
        store.addPref(user, NotificationChannel.WEBHOOK, enabled = false)

        router.route(user.id!!, event)

        assertEquals(1, dispatcher.stomps)
        assertEquals(0, dispatcher.webhooks)
    }

    @Test
    fun `routes to WEBHOOK when enabled`() {
        store.addPref(user, NotificationChannel.STOMP, enabled = false)
        store.addPref(user, NotificationChannel.WEBHOOK, enabled = true)

        router.route(user.id!!, event)

        assertEquals(0, dispatcher.stomps)
        assertEquals(1, dispatcher.webhooks)
    }

    @Test
    fun `routes to multiple enabled channels`() {
        store.addPref(user, NotificationChannel.STOMP, enabled = true)
        store.addPref(user, NotificationChannel.EMAIL, enabled = true)

        router.route(user.id!!, event)

        assertEquals(1, dispatcher.stomps)
        assertEquals(1, dispatcher.emails)
        assertEquals(0, dispatcher.webhooks)
    }

    @Test
    fun `dispatch failure in one channel does not block others`() {
        store.addPref(user, NotificationChannel.STOMP, enabled = true)
        store.addPref(user, NotificationChannel.GRAPHQL, enabled = true)
        dispatcher.graphqlThrows = true

        router.route(user.id!!, event)

        assertEquals(1, dispatcher.stomps)
    }
}
