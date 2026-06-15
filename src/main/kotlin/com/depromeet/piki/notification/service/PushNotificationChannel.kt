package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.fcm.service.FcmMessageSender
import com.depromeet.piki.notification.fcm.service.UserDeviceService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

// 알림을 FCM 푸시로 전달하는 채널(#245). @Component 라 dispatcher 의 List<NotificationChannel> 에
// SseNotificationChannel 과 형제로 자동 합류한다 — dispatcher·엔티티·핸들러는 건드리지 않는다.
//
// SSE 채널과 같은 2계층: 이 채널은 얇은 진입점이고, 실제 발송·죽은 토큰 판정은 FcmMessageSender(외부 경계),
// 토큰 조회·정리(트랜잭션)는 UserDeviceService 가 맡는다. send 는 dispatcher 가 트랜잭션 밖에서 호출하므로
// 외부 FCM 호출이 DB 커넥션을 잡지 않는다(CLAUDE.md "외부 호출은 트랜잭션 밖").
//
// FcmMessageSender 는 ObjectProvider 로 받는다 — FirebaseApp 키가 없는 환경에선 FirebaseMessageSender 빈이
// 안 떠서, getIfAvailable() 이 null 이면 발송을 건너뛴다(토큰 수집 API 는 키 없이도 동작).
@Component
class PushNotificationChannel(
    private val senderProvider: ObjectProvider<FcmMessageSender>,
    private val userDeviceService: UserDeviceService,
) : NotificationChannel {
    private val log = LoggerFactory.getLogger(javaClass)

    // sender 부재는 로컬에선 정상(키 없음)이지만 운영에선 설정 누락 신호다. 매 발송마다 찍으면
    // 로컬·미설정 환경에서 로그가 폭주하므로, 인스턴스 생애 1회만 warn 해 관측은 되되 스팸은 막는다.
    private val senderMissingWarned = AtomicBoolean(false)

    override fun send(
        userId: UUID,
        notification: Notification,
    ) {
        val sender =
            senderProvider.getIfAvailable() ?: run {
                if (senderMissingWarned.compareAndSet(false, true)) {
                    log.warn("FCM sender 미주입 — 푸시 발송 건너뜀 (FIREBASE_SERVICE_ACCOUNT 미설정?). 이후 동일 상황은 로그 생략")
                }
                return
            }
        val tokens = userDeviceService.findTokens(userId)
        if (tokens.isEmpty()) return
        val result = sender.send(tokens, notification)
        userDeviceService.removeStaleTokens(result.staleTokens)
    }
}
