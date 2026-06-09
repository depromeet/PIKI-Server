package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationCursor
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class NotificationRepositoryImpl(
    private val notificationJpaRepository: NotificationJpaRepository,
) : NotificationRepository {
    override fun save(notification: Notification): Notification = notificationJpaRepository.save(notification)

    override fun hardDeleteAllByUserId(userId: UUID): Int = notificationJpaRepository.hardDeleteAllByUserId(userId)

    override fun findPage(
        userId: UUID,
        cursor: NotificationCursor?,
        limit: Int,
    ): List<Notification> {
        val limited = Limit.of(limit)
        cursor ?: return notificationJpaRepository.findByUserIdOrderByIdDesc(userId, limited)
        return notificationJpaRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor.lastNotificationId, limited)
    }

    override fun countUnread(userId: UUID): Long = notificationJpaRepository.countByUserIdAndIsReadFalse(userId)

    override fun markRead(
        userId: UUID,
        ids: List<Long>,
    ): Int = notificationJpaRepository.markReadByUserIdAndIds(userId, ids)

    override fun markAllRead(userId: UUID): Int = notificationJpaRepository.markAllReadByUserId(userId)
}
