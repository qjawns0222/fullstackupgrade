package com.example.demo.notification

import com.example.demo.entity.User

class FakeNotificationPreferenceStore : NotificationPreferenceStore {
    private val store = mutableListOf<UserNotificationPreference>()
    private var idSeq = 1L

    fun addPref(user: User, channel: NotificationChannel, enabled: Boolean) {
        store.add(UserNotificationPreference(id = idSeq++, user = user, channel = channel, enabled = enabled))
    }

    override fun findByUserId(userId: Long) = store.filter { it.user.id == userId }

    override fun findByUserIdAndChannel(userId: Long, channel: NotificationChannel) =
        store.firstOrNull { it.user.id == userId && it.channel == channel }

    override fun save(pref: UserNotificationPreference): UserNotificationPreference {
        val idx = store.indexOfFirst { it.user.id == pref.user.id && it.channel == pref.channel }
        return if (idx >= 0) { store[idx] = pref; pref }
        else { pref.also { it.id = idSeq++; store.add(it) } }
    }

    override fun deleteByUserIdAndChannel(userId: Long, channel: NotificationChannel) {
        store.removeIf { it.user.id == userId && it.channel == channel }
    }
}

class FakeNotificationDispatcher : NotificationDispatcher {
    var stomps = 0
    var graphqls = 0
    var webhooks = 0
    var emails = 0
    var graphqlThrows = false

    override fun dispatchStomp(userId: Long, event: NotificationEvent) { stomps++ }
    override fun dispatchGraphql(userId: Long, event: NotificationEvent) {
        if (graphqlThrows) throw RuntimeException("graphql error")
        graphqls++
    }
    override fun dispatchWebhook(userId: Long, event: NotificationEvent) { webhooks++ }
    override fun dispatchEmail(userId: Long, event: NotificationEvent) { emails++ }
}
