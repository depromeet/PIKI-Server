package com.depromeet.piki.notification.repository

import com.depromeet.piki.notification.domain.Notification

// 토대는 저장만 필요하다. 목록·읽음·unread-count 조회 메서드는 #246 이 추가한다.
interface NotificationRepository {
    fun save(notification: Notification): Notification
}
