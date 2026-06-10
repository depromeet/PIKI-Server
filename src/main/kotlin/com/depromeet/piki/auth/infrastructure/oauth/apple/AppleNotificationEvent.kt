package com.depromeet.piki.auth.infrastructure.oauth.apple

import tools.jackson.databind.json.JsonMapper

// Apple 서버-서버 알림 JWT 의 events 클레임을 파싱한 결과. events 는 payload JWT 안에 JSON 이
// 문자열로 한 번 더 인코딩되어 들어온다 (예: "{\"type\":\"account-delete\",\"sub\":\"00..\",\"event_time\":1700..}").
// 서명 검증과 분리된 순수 파싱이라 단위 테스트로 분기를 망라한다.
data class AppleNotificationEvent(
    val type: AppleNotificationEventType,
    val sub: String?,
) {
    companion object {
        private val jsonMapper = JsonMapper.builder().build()

        // events 클레임(JSON 문자열)을 파싱한다. type 은 enum 으로 정규화하고, sub 는 공백이면 null 로 둔다.
        // 우리가 보는 필드는 type·sub 뿐이라 나머지(email·event_time 등)는 무시한다.
        //
        // 탈퇴·세션 종료는 대상 유저를 sub 로 특정해야 한다. 그 두 타입의 sub 누락은 비정상 payload(형식 오류)이므로
        // 예외로 막는다 — 여기서 멱등 무시(200)로 흡수하면 실제 처리 없이 200 이 나가 Apple 상태와 어긋난다.
        // (호출자 verify 가 이 예외를 invalidSignature 401 로 변환한다.) email·unknown 은 sub 없이도 로그만 남기므로 허용.
        fun parse(eventsJson: String): AppleNotificationEvent {
            val node = jsonMapper.readTree(eventsJson)
            val type = AppleNotificationEventType.from(node.get("type")?.asString())
            val sub = node.get("sub")?.asString()?.trim()?.ifBlank { null }
            return when (type) {
                AppleNotificationEventType.ACCOUNT_DELETE, AppleNotificationEventType.CONSENT_REVOKED ->
                    AppleNotificationEvent(type, sub ?: throw IllegalArgumentException("sub is required for $type"))
                else -> AppleNotificationEvent(type, sub)
            }
        }
    }
}
