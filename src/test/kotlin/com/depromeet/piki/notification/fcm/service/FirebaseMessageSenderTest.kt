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
    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode::class, names = ["UNREGISTERED", "INVALID_ARGUMENT"])
    fun `무효 토큰 에러코드는 정리 대상이다`(code: MessagingErrorCode) {
        assertTrue(FirebaseMessageSender.isStaleToken(code))
    }

    @ParameterizedTest
    @EnumSource(
        value = MessagingErrorCode::class,
        names = ["UNREGISTERED", "INVALID_ARGUMENT"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `일시 오류 에러코드는 재시도 위해 보존한다`(code: MessagingErrorCode) {
        assertFalse(FirebaseMessageSender.isStaleToken(code))
    }

    @Test
    fun `에러코드가 없으면 보존한다`() {
        assertFalse(FirebaseMessageSender.isStaleToken(null))
    }
}
