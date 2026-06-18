package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification

// FCM 발송의 외부 경계(우리 바깥 의존성). 구현은 FirebaseMessaging 호출을 숨긴다.
// 통합 테스트는 이 인터페이스를 stub 으로 교체해 실제 FCM 호출 없이 채널 fan-out·토큰 정리를 검증한다.
interface FcmMessageSender {
    // tokens 로 멀티캐스트 발송하고, 결과(죽은 토큰·성공 수·코드별 실패 수)를 반환한다(#489).
    // 죽은 토큰은 호출자가 정리하고, 성공/실패 집계는 공지 브로드캐스트가 건별 행에 기록한다.
    //
    // badge — 수신자의 전체 안읽음 수. iOS aps.badge·Android setNotificationCount 로 실어 OS 아이콘 badge 를 갱신한다(#487).
    // tokens 는 한 수신자의 기기들이라 badge 도 그 수신자 값 하나다(여러 유저 토큰을 한 멀티캐스트에 섞지 않는다).
    fun send(
        tokens: List<String>,
        notification: Notification,
        badge: Int,
    ): FcmSendResult

    // 읽음 처리 후 갱신된 안읽음 수만 data-only(silent) 푸시로 전달해 OS 아이콘 badge 를 내린다(#487, 멀티 디바이스 동기화).
    // 표시할 알림이 없으므로 notification 블록 없이 보낸다 — iOS 는 aps.badge(content-available), Android 는 클라
    // 백그라운드 핸들러가 data.unreadCount 로 notifee badge 를 갱신한다. 죽은 토큰 정리 계약은 send 와 동일.
    fun sendBadgeSync(
        tokens: List<String>,
        badge: Int,
    ): FcmSendResult
}
