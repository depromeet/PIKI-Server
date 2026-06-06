package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification

// FCM 발송의 외부 경계(우리 바깥 의존성). 구현은 FirebaseMessaging 호출을 숨긴다.
// 통합 테스트는 이 인터페이스를 stub 으로 교체해 실제 FCM 호출 없이 채널 fan-out·토큰 정리를 검증한다.
interface FcmMessageSender {
    // tokens 로 멀티캐스트 발송하고, 발송 결과 죽은(앱 삭제·무효) 토큰 목록을 반환한다 — 호출자가 정리한다.
    fun send(
        tokens: List<String>,
        notification: Notification,
    ): List<String>
}
