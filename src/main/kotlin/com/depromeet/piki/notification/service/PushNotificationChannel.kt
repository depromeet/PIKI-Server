package com.depromeet.piki.notification.service

import com.depromeet.piki.notification.domain.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

// FCM 푸시 채널의 토대 자리 — 아직 NoOp(로그만)이다.
// 실구현(FirebaseMessaging 발송)은 #245 가 이 빈을 교체한다.
@Component
class PushNotificationChannel : NotificationChannel {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        userId: UUID,
        notification: Notification,
    ) {
        log.info("[NoOp Push] userId={} type={} refId={}", userId, notification.type, notification.refId)
    }
}
