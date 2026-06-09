package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.NotificationCursor
import com.depromeet.piki.notification.domain.NotificationHistorySize
import com.depromeet.piki.notification.repository.NotificationRepository
import com.depromeet.piki.notification.service.dto.NotificationHistoryPage
import com.depromeet.piki.notification.service.dto.NotificationReadCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// 알림 히스토리 조회 + 읽음 처리(#246). 발송(채널 fan-out)과 무관한 읽기/상태 변경 경계다.
@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {
    // 본인 알림을 최신순으로 한 페이지 + 안읽음 수 동봉. hasNext 판단을 위해 한 건 더 조회하고 초과분은 잘라낸다.
    @Transactional(readOnly = true)
    fun getHistory(
        userId: UUID,
        rawCursor: String?,
        rawSize: Int?,
    ): NotificationHistoryPage {
        val cursor = NotificationCursor.parse(rawCursor)
        val size = NotificationHistorySize.of(rawSize).value
        val fetched = notificationRepository.findPage(userId, cursor, size + 1)
        val hasNext = fetched.size > size
        val page = fetched.take(size)
        val unreadCount = notificationRepository.countUnread(userId)
        val nextCursor =
            page
                .lastOrNull()
                ?.getId()
                ?.toString()
                .takeIf { hasNext }
        return NotificationHistoryPage(notifications = page, unreadCount = unreadCount, nextCursor = nextCursor, hasNext = hasNext)
    }

    // 읽음 처리 — 명령(All/Ids)별 벌크 UPDATE. 본인 소유만 반영(소유 검증은 쿼리의 user_id 조건이 겸한다). 멱등.
    @Transactional
    fun read(
        userId: UUID,
        command: NotificationReadCommand,
    ) {
        when (command) {
            NotificationReadCommand.All -> notificationRepository.markAllRead(userId)
            is NotificationReadCommand.Ids -> notificationRepository.markRead(userId, command.ids)
        }
    }
}
