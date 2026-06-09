package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationCursor
import java.util.UUID

interface NotificationRepository {
    fun save(notification: Notification): Notification

    // 탈퇴 cascade — 그 수신자의 알림을 일괄 하드삭제하고 영향 건수를 돌려준다. 멱등(없으면 0건).
    fun hardDeleteAllByUserId(userId: UUID): Int

    // 본인 알림을 최신순(id desc)으로 최대 limit 건. cursor 가 있으면 그 id 미만만(다음 페이지).
    fun findPage(
        userId: UUID,
        cursor: NotificationCursor?,
        limit: Int,
    ): List<Notification>

    // 본인 안읽음 수(badge).
    fun countUnread(userId: UUID): Long

    // 지정 id 중 본인 소유 + 안읽음만 read 로 (타인/없는 id 무영향). 영향 건수 반환. 멱등.
    fun markRead(
        userId: UUID,
        ids: List<Long>,
    ): Int

    // 본인 안읽음 전부 read. 영향 건수 반환. 멱등.
    fun markAllRead(userId: UUID): Int
}
