package com.depromeet.piki.support

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.fcm.service.FcmMessageSender
import com.depromeet.piki.notification.fcm.service.FcmSendResult

// 외부 FCM(FirebaseMessaging) 호출을 통합 테스트에서 격리하기 위한 stub(#245).
// 모든 통합 테스트가 같은 IntegrationTestSupport 컨텍스트를 공유하므로 이 빈도 단일 인스턴스다.
// 매 테스트가 본문에서 onSend 람다를 명시적으로 세팅한다 (셋업 hook · default 리셋 금지).
//
// default onSend 는 throw 다. 명시 세팅을 빠뜨리면 즉시 IllegalStateException 으로 깨져
// "이전 테스트의 onSend 가 살아남아 다음 테스트에 영향을 주는" 함정을 차단한다.
// (FCM 토큰이 없는 대다수 통합 테스트는 PushNotificationChannel 이 토큰 조회 후 early return 하므로
//  이 stub 에 도달하지 않는다 — 토큰을 등록한 발송 검증 테스트만 onSend 를 세팅한다.)
// 호출 여부·전달 토큰 검증은 테스트 본문에서 onSend 람다로 캡처한다(공유 인스턴스 누수를 피해 stub 에 카운터를
// 두지 않는다 — 각 테스트가 자기 람다에 잡아 그 메서드만 보고 시나리오를 완결한다).
class StubFcmMessageSender : FcmMessageSender {
    // 람다는 죽은 토큰 리스트만 반환한다(기존 시나리오 호환) — 그 결과를 FcmSendResult 로 감싼다.
    // 죽은 토큰 외 토큰은 성공으로 친다(성공 수 = 토큰 − 죽은 토큰). 코드별 실패 분포 검증이 필요한 테스트가
    // 생기면 onSendResult 로 직접 FcmSendResult 를 돌려주도록 확장한다.
    var onSend: (List<String>, Notification) -> List<String> = { _, _ ->
        error("stub.onSend 를 테스트 본문에서 명시 세팅해야 한다. CLAUDE.md '테스트 셋업 원칙' 참고.")
    }

    override fun send(
        tokens: List<String>,
        notification: Notification,
    ): FcmSendResult {
        val stale = onSend(tokens, notification)
        val failureByCode = if (stale.isEmpty()) emptyMap() else mapOf("UNREGISTERED" to stale.size)
        return FcmSendResult(staleTokens = stale, successCount = tokens.size - stale.size, failureByCode = failureByCode)
    }
}
