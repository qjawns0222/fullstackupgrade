package com.example.demo.notification

import com.example.demo.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class NotificationPreferenceService(
    private val preferenceStore: NotificationPreferenceStore
) {

    fun getPreferences(userId: Long): List<NotificationPreferenceResponse> {
        val existing = preferenceStore.findByUserId(userId).associateBy { it.channel }
        return NotificationChannel.entries.map { channel ->
            val pref = existing[channel]
            NotificationPreferenceResponse(
                channel = channel,
                enabled = pref?.enabled ?: false,
                updatedAt = pref?.updatedAt ?: LocalDateTime.now()
            )
        }
    }

    @Transactional
    fun upsertPreference(user: User, request: NotificationPreferenceRequest): NotificationPreferenceResponse {
        val pref = preferenceStore.findByUserIdAndChannel(user.id!!, request.channel)
            ?.apply {
                enabled = request.enabled
                updatedAt = LocalDateTime.now()
            }
            ?: UserNotificationPreference(
                user = user,
                channel = request.channel,
                enabled = request.enabled
            )
        val saved = preferenceStore.save(pref)
        return NotificationPreferenceResponse(saved.channel, saved.enabled, saved.updatedAt)
    }

    @Transactional
    fun deletePreference(userId: Long, channel: NotificationChannel) {
        preferenceStore.deleteByUserIdAndChannel(userId, channel)
    }
}
