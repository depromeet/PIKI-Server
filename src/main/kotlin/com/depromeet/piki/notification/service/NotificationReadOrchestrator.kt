package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.NotificationCategory
import com.depromeet.piki.notification.service.dto.NotificationReadCommand
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

// 읽음 처리(#246) + 갱신 badge 동기화(#487)를 묶는 얇은 오케스트레이터. 트랜잭션이 없다(@Transactional 미부착) —
// 읽음 UPDATE 는 NotificationService.read(별도 빈의 @Transactional)가 짧은 트랜잭션으로 커밋하고,
// silent 푸시(외부 FCM 호출)는 그 커밋 이후 트랜잭션 밖에서 돈다(CLAUDE.md "외부 호출은 트랜잭션 밖").
// dispatcher 가 persistence.save(tx) 후 channel.send(밖)를 호출하는 것과 같은 결이다.
@Component
class NotificationReadOrchestrator(
    private val notificationService: NotificationService,
    private val pushNotificationChannel: PushNotificationChannel,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 읽음 처리 후 갱신 안읽음 수를 반환하고(읽은 기기는 응답 body 로 즉시 badge 미러링), 같은 유저의 다른 기기엔
    // silent 푸시로 OS 아이콘 badge 를 내린다. 푸시 실패는 best-effort 로 흡수한다 — 읽음은 이미 커밋됐고,
    // 못 받은 기기는 재진입 시 GET /notifications 로 보정되므로 푸시 실패가 읽음 응답을 깨면 안 된다.
    fun readAndSyncBadge(
        userId: UUID,
        command: NotificationReadCommand,
    ): Map<NotificationCategory, Long> {
        val unread = notificationService.read(userId, command)
        runCatching { pushNotificationChannel.syncBadge(userId, unread.toBadgeCount()) }
            .onFailure { e -> log.warn("읽음 후 badge 동기화 푸시 실패 userId={}", userId, e) }
        return unread
    }
}
