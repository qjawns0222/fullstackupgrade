package com.example.demo.notification

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JpaNotificationPreferenceStore(
    private val repo: NotificationPreferenceRepository
) : NotificationPreferenceStore {

    override fun findByUserId(userId: Long) = repo.findAllByUserId(userId)

    override fun findByUserIdAndChannel(userId: Long, channel: NotificationChannel) =
        repo.findByUserIdAndChannel(userId, channel)

    override fun save(pref: UserNotificationPreference) = repo.save(pref)

    @Transactional
    override fun deleteByUserIdAndChannel(userId: Long, channel: NotificationChannel) =
        repo.deleteByUserIdAndChannel(userId, channel)
}
