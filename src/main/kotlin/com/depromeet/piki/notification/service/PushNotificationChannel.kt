package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.fcm.service.FcmMessageSender
import com.depromeet.piki.notification.fcm.service.UserDeviceService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.UUID

// 알림을 FCM 푸시로 전달하는 채널(#245). @Component 라 dispatcher 의 List<NotificationChannel> 에
// SseNotificationChannel 과 형제로 자동 합류한다 — dispatcher·엔티티·핸들러는 건드리지 않는다.
//
// SSE 채널과 같은 2계층: 이 채널은 얇은 진입점이고, 실제 발송·죽은 토큰 판정은 FcmMessageSender(외부 경계),
// 토큰 조회·정리(트랜잭션)는 UserDeviceService 가 맡는다. send 는 dispatcher 가 트랜잭션 밖에서 호출하므로
// 외부 FCM 호출이 DB 커넥션을 잡지 않는다(CLAUDE.md "외부 호출은 트랜잭션 밖").
//
// FcmMessageSender 는 ObjectProvider 로 받는다 — FirebaseApp 키가 없는 환경에선 FirebaseMessageSender 빈이
// 안 떠서, getIfAvailable() 이 null 이면 발송을 조용히 건너뛴다(토큰 수집 API 는 키 없이도 동작).
@Component
class PushNotificationChannel(
    private val senderProvider: ObjectProvider<FcmMessageSender>,
    private val userDeviceService: UserDeviceService,
) : NotificationChannel {
    override fun send(
        userId: UUID,
        notification: Notification,
    ) {
        val sender = senderProvider.getIfAvailable() ?: return
        val tokens = userDeviceService.findTokens(userId)
        if (tokens.isEmpty()) return
        val staleTokens = sender.send(tokens, notification)
        userDeviceService.removeStaleTokens(staleTokens)
    }
}
