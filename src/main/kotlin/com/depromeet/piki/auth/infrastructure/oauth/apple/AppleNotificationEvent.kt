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
        fun parse(eventsJson: String): AppleNotificationEvent {
            val node = jsonMapper.readTree(eventsJson)
            val type = AppleNotificationEventType.from(node.get("type")?.asString())
            val sub = node.get("sub")?.asString()?.ifBlank { null }
            return AppleNotificationEvent(type = type, sub = sub)
        }
    }
}
