package com.depromeet.piki.auth.infrastructure.oauth.apple

// Apple 서버-서버 알림 payload(JWT)의 서명·issuer·aud 를 검증하고 events 를 파싱해 돌려준다.
// Apple JWKS(/auth/keys)를 가져오는 외부 호출 경계이므로 인터페이스로 분리한다 — 통합 테스트는
// 이 경계를 stub 로 격리한다(모킹 정책: 외부 호출 경계만 격리). 운영 구현은 AppleOAuthClient 가
// 맡아 id_token 검증과 JWKS 캐시를 한 인스턴스에서 공유한다(/auth/keys 중복 호출 방지).
interface AppleNotificationVerifier {
    // 검증 성공 시 파싱된 이벤트를 반환한다. 서명/issuer/aud 실패·형식 오류면 AppleNotificationException(401).
    fun verify(payloadJwt: String): AppleNotificationEvent
}
