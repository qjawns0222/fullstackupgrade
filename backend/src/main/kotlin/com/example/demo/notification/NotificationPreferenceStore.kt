package com.example.demo.notification

interface NotificationPreferenceStore {
    fun findByUserId(userId: Long): List<UserNotificationPreference>
    fun findByUserIdAndChannel(userId: Long, channel: NotificationChannel): UserNotificationPreference?
    fun save(pref: UserNotificationPreference): UserNotificationPreference
    fun deleteByUserIdAndChannel(userId: Long, channel: NotificationChannel)
}
