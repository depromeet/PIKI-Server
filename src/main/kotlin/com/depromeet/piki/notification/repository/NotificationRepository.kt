package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import java.time.LocalDateTime
import java.util.UUID

// 토대는 저장만 필요하다. 목록·읽음·unread-count 조회 메서드는 #246 이 추가한다.
interface NotificationRepository {
    fun save(notification: Notification): Notification

    // 탈퇴 cascade — 그 수신자의 활성 알림을 일괄 soft-delete 하고 영향 건수를 돌려준다. 멱등.
    fun softDeleteAllByUserId(
        userId: UUID,
        now: LocalDateTime,
    ): Int

    // 30일 파기 — 그 수신자의 알림을 영구 하드삭제하고 영향 건수를 돌려준다.
    fun hardDeleteAllByUserId(userId: UUID): Int
}
