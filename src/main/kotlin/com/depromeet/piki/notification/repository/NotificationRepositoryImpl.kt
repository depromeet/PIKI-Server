package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class NotificationRepositoryImpl(
    private val notificationJpaRepository: NotificationJpaRepository,
) : NotificationRepository {
    override fun save(notification: Notification): Notification = notificationJpaRepository.save(notification)

    override fun hardDeleteAllByUserId(userId: UUID): Int = notificationJpaRepository.hardDeleteAllByUserId(userId)
}
