package com.depromeet.piki.notification.fcm.service

import com.google.firebase.messaging.MessagingErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 죽은 토큰 판정 분기 망라(#245). FirebaseMessaging 응답과 무관한 순수 정책이라 FirebaseApp 없이 단위로 검증한다.
// (멀티캐스트 chunk 루프·발송 실패 스킵은 FirebaseMessaging 호출에 강결합이라 여기서 다루지 않는다 —
//  채널 레벨 fan-out·정리는 PushNotificationChannelIntegrationTest 가 stub 으로 덮는다.)
class FirebaseMessageSenderTest {
    @Test
    fun `UNREGISTERED 만 정리 대상이다`() {
        assertTrue(FirebaseMessageSender.isStaleToken(MessagingErrorCode.UNREGISTERED))
    }

    // INVALID_ARGUMENT 는 토큰이 아닌 요청·메시지 문제에도 와서 보존한다(정상 토큰 대량 삭제 방지).
    // 그 외 일시 오류도 재시도 위해 보존.
    @ParameterizedTest
    @EnumSource(
        value = MessagingErrorCode::class,
        names = ["UNREGISTERED"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `UNREGISTERED 외 에러코드는 보존한다`(code: MessagingErrorCode) {
        assertFalse(FirebaseMessageSender.isStaleToken(code))
    }

    @Test
    fun `에러코드가 없으면 보존한다`() {
        assertFalse(FirebaseMessageSender.isStaleToken(null))
    }
}
