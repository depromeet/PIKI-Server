package com.depromeet.piki.auth.infrastructure.oauth.apple

// Apple 서버-서버 알림(events)의 type. Apple 이 보내는 원문 문자열을 우리 enum 으로 정규화한다.
// 모르는 타입은 UNKNOWN 으로 흡수해 (Apple 이 타입을 늘려도) 처리를 깨뜨리지 않고 로그만 남긴다.
enum class AppleNotificationEventType {
    ACCOUNT_DELETE, // Apple ID 자체 삭제 → 회원 탈퇴
    CONSENT_REVOKED, // 앱-Apple 연결 해제 → 세션 종료(로그아웃), 계정·데이터는 유지
    EMAIL_DISABLED, // Private Relay 이메일 전달 중단 → 로그만 (우리는 메일 발송 안 함)
    EMAIL_ENABLED, // Private Relay 이메일 전달 재개 → 로그만
    UNKNOWN, // 미지원/미상 타입 → 로그만
    ;

    companion object {
        // Apple 원문(예: "account-delete")을 enum 으로. 미상·null 은 UNKNOWN.
        fun from(raw: String?): AppleNotificationEventType {
            val normalized = raw?.trim()?.lowercase() ?: return UNKNOWN
            return when (normalized) {
                "account-delete" -> ACCOUNT_DELETE
                "consent-revoked" -> CONSENT_REVOKED
                "email-disabled" -> EMAIL_DISABLED
                "email-enabled" -> EMAIL_ENABLED
                else -> UNKNOWN
            }
        }
    }
}
