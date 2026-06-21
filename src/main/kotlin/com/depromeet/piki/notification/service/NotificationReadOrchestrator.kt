package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.controller.dto.UnreadBadgeChanged
import com.depromeet.piki.notification.domain.NotificationCategory
import com.depromeet.piki.notification.service.dto.NotificationReadCommand
import com.depromeet.piki.notification.sse.LocalSseDelivery
import org.springframework.stereotype.Component
import java.util.UUID

// 읽음 처리(#246) + 갱신 badge 동기화(#487)를 묶는 얇은 오케스트레이터. 트랜잭션이 없다(@Transactional 미부착) —
// 읽음 UPDATE 는 NotificationService.read(별도 빈의 @Transactional)가 짧은 트랜잭션으로 커밋하고,
// silent 푸시는 PushNotificationChannel.syncBadge(@Async)가 그 커밋 이후 응답 경로 밖(notificationExecutor)에서 돈다.
// dispatcher 가 persistence.save(tx) 후 비동기 워커에서 channel.send(밖)를 호출하는 것과 같은 결이다.
@Component
class NotificationReadOrchestrator(
    private val notificationService: NotificationService,
    private val pushNotificationChannel: PushNotificationChannel,
    private val localDelivery: LocalSseDelivery,
) {
    // 읽음 처리 후 갱신 안읽음 수를 반환하고(읽은 기기는 응답 body 로 즉시 badge 미러링), 같은 유저의 다른 기기엔
    // 두 경로로 badge 를 맞춘다: 온라인(열린 SSE) 기기는 silent-sync(UNREAD_BADGE)로 인앱 배지를, 오프라인 기기는
    // FCM silent 푸시로 OS 아이콘 badge 를. SSE 가 인앱 진실이고 FCM 은 앱이 꺼진 기기의 OS 트레이 보조라, 둘을
    // 함께 보내야 온라인·오프라인 기기가 모두 같은 수로 수렴한다(FCM-only 면 앱 열어둔 기기의 인앱 숫자가 안 바뀜).
    // syncBadge 는 @Async 라 읽음 응답이 FCM latency 에 묶이지 않고, 푸시 실패도 그 워커 안에서 best-effort 로
    // 흡수된다(읽음은 이미 커밋, 못 받은 기기는 재진입 시 GET 으로 보정).
    fun readAndSyncBadge(
        userId: UUID,
        command: NotificationReadCommand,
    ): Map<NotificationCategory, Long> {
        val unread = notificationService.read(userId, command)
        localDelivery.deliverSilentSync(listOf(userId), UnreadBadgeChanged.of(unread))
        pushNotificationChannel.syncBadge(userId, unread.toBadgeCount())
        return unread
    }
}
