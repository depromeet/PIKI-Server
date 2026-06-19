package com.depromeet.piki.notification.fcm.service

import com.google.firebase.messaging.MessagingErrorCode

// FCM 발송 실패 사유(#489) — Firebase MessagingErrorCode 를 우리 enum 으로 고정해 집계·표시를 타입 안전하게 한다.
// 문자열 키 대신 enum 이면 오타·미정의 코드가 컴파일에서 걸리고, 결과 화면·분석이 정해진 값 집합만 다룬다.
// 값은 Firebase 의 MessagingErrorCode 와 1:1 로 맞춰 from() 이 name 으로 매핑되게 한다(+ 미매핑 UNKNOWN).
enum class FcmFailureCode {
    UNREGISTERED, // 앱 삭제·토큰 폐기 → 정리 대상(stale)
    SENDER_ID_MISMATCH, // 다른 Firebase 프로젝트에서 발급된 토큰
    INVALID_ARGUMENT, // 요청·메시지 파라미터 오류(토큰 무관일 수 있어 보존)
    QUOTA_EXCEEDED, // 발송 쿼터 초과
    UNAVAILABLE, // FCM 일시 장애(재시도 대상)
    THIRD_PARTY_AUTH_ERROR, // APNS/WebPush 인증 오류
    INTERNAL, // FCM 내부 오류
    UNKNOWN, // 코드 없음·우리 enum 에 없는 신규 코드
    ;

    companion object {
        // Firebase 코드 → 우리 enum. null 이거나 우리가 모르는 값이면 UNKNOWN — 미래에 FCM 이 코드를 늘려도 안 깨진다.
        fun from(code: MessagingErrorCode?): FcmFailureCode = code?.let { runCatching { valueOf(it.name) }.getOrDefault(UNKNOWN) } ?: UNKNOWN
    }
}
