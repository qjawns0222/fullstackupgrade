package com.example.demo.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NotificationPreferenceRepository : JpaRepository<UserNotificationPreference, Long> {
    fun findAllByUserId(userId: Long): List<UserNotificationPreference>
    fun findByUserIdAndChannel(userId: Long, channel: NotificationChannel): UserNotificationPreference?

    @Modifying
    @Query("DELETE FROM UserNotificationPreference p WHERE p.user.id = :userId AND p.channel = :channel")
    fun deleteByUserIdAndChannel(userId: Long, channel: NotificationChannel)
}
