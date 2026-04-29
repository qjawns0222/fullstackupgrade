package com.example.demo.notification

import com.example.demo.entity.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationPreferenceServiceTest {

    private lateinit var store: FakeNotificationPreferenceStore
    private lateinit var service: NotificationPreferenceService

    private val user = User(id = 1L, username = "testuser", password = "pass", role = "ROLE_USER")

    @BeforeEach
    fun setUp() {
        store = FakeNotificationPreferenceStore()
        service = NotificationPreferenceService(store)
    }

    @Test
    fun `getPreferences returns all channels with false when no preferences saved`() {
        val prefs = service.getPreferences(user.id!!)
        assertEquals(NotificationChannel.entries.size, prefs.size)
        assertTrue(prefs.all { !it.enabled })
    }

    @Test
    fun `upsertPreference creates new preference when none exists`() {
        val result = service.upsertPreference(user, NotificationPreferenceRequest(NotificationChannel.STOMP, true))
        assertTrue(result.enabled)
        assertEquals(NotificationChannel.STOMP, result.channel)
    }

    @Test
    fun `upsertPreference updates existing preference`() {
        service.upsertPreference(user, NotificationPreferenceRequest(NotificationChannel.EMAIL, true))
        val updated = service.upsertPreference(user, NotificationPreferenceRequest(NotificationChannel.EMAIL, false))
        assertFalse(updated.enabled)
    }

    @Test
    fun `getPreferences reflects saved preferences`() {
        service.upsertPreference(user, NotificationPreferenceRequest(NotificationChannel.WEBHOOK, true))
        val prefs = service.getPreferences(user.id!!)
        val webhookPref = prefs.first { it.channel == NotificationChannel.WEBHOOK }
        assertTrue(webhookPref.enabled)
    }

    @Test
    fun `deletePreference removes the preference`() {
        service.upsertPreference(user, NotificationPreferenceRequest(NotificationChannel.STOMP, true))
        service.deletePreference(user.id!!, NotificationChannel.STOMP)
        val prefs = service.getPreferences(user.id!!)
        val stompPref = prefs.first { it.channel == NotificationChannel.STOMP }
        assertFalse(stompPref.enabled)
    }
}
