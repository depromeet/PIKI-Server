package com.depromeet.piki.notification.fcm.service

import com.google.firebase.messaging.MessagingErrorCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals

// FcmFailureCode 가 Firebase MessagingErrorCode 전체를 빠짐없이 같은 이름으로 흡수하는지 검증한다(#489).
// 가드: Firebase 가 코드를 늘렸는데 우리 enum 에 안 더하면 from()→UNKNOWN 으로 떨어져 이 테스트가 깨진다 → 누락을 컴파일/CI 에서 잡는다.
class FcmFailureCodeTest {
    @ParameterizedTest
    @EnumSource(MessagingErrorCode::class)
    fun `모든 FCM MessagingErrorCode 는 같은 이름의 FcmFailureCode 로 매핑된다`(code: MessagingErrorCode) {
        assertEquals(code.name, FcmFailureCode.from(code).name)
    }

    @Test
    fun `코드가 없으면 UNKNOWN 이다`() {
        assertEquals(FcmFailureCode.UNKNOWN, FcmFailureCode.from(null))
    }
}
