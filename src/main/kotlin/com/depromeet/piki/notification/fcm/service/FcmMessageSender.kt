package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification

// FCM 발송의 외부 경계(우리 바깥 의존성). 구현은 FirebaseMessaging 호출을 숨긴다.
// 통합 테스트는 이 인터페이스를 stub 으로 교체해 실제 FCM 호출 없이 채널 fan-out·토큰 정리를 검증한다.
interface FcmMessageSender {
    // tokens 로 멀티캐스트 발송하고, 결과(죽은 토큰·성공 수·코드별 실패 수)를 반환한다(#489).
    // 죽은 토큰은 호출자가 정리하고, 성공/실패 집계는 공지 브로드캐스트가 건별 행에 기록한다.
    fun send(
        tokens: List<String>,
        notification: Notification,
    ): FcmSendResult
}
