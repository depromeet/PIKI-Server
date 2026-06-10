package com.depromeet.piki.support

import com.depromeet.piki.auth.infrastructure.oauth.apple.AppleNotificationEvent
import com.depromeet.piki.auth.infrastructure.oauth.apple.AppleNotificationVerifier

// Apple JWKS 검증(외부 호출 경계) 격리용 programmable stub. 운영 구현(AppleOAuthClient)은
// oauth.client.enabled=false 로 꺼져 있어 이 stub 이 유일한 AppleNotificationVerifier 다.
// default 람다는 throw — 명시 세팅을 빠뜨리면 호출 시점에 즉시 깨지도록 강제한다.
// 서명 실패(401) 시나리오는 verifyStub 에 throw 를 세팅해 재현한다.
class StubAppleNotificationVerifier : AppleNotificationVerifier {
    var verifyStub: (String) -> AppleNotificationEvent = {
        error("stub.verifyStub 를 테스트 본문에서 명시 세팅해야 한다.")
    }

    override fun verify(payloadJwt: String): AppleNotificationEvent = verifyStub(payloadJwt)
}
