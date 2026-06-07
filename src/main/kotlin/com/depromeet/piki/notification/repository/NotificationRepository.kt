package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification
import java.util.UUID

// 토대는 저장만 필요하다. 목록·읽음·unread-count 조회 메서드는 #246 이 추가한다.
interface NotificationRepository {
    fun save(notification: Notification): Notification

    // 탈퇴 cascade — 그 수신자의 알림을 일괄 하드삭제하고 영향 건수를 돌려준다. 멱등(없으면 0건).
    fun hardDeleteAllByUserId(userId: UUID): Int
}
