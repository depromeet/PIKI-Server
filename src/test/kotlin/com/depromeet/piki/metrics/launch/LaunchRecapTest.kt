package com.depromeet.piki.metrics.launch

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// LaunchRecap 의 파생 계산(비율·평균) 분기 — 특히 0 분모 처리를 망라한다. 순수 계산이라 Spring·DB 없이 검증한다.
class LaunchRecapTest {
    @Test
    fun `pct 는 분모가 0이면 0, 아니면 내림 정수 퍼센트를 반환한다`() {
        assertEquals(0, LaunchRecap.pct(0, 0))
        assertEquals(0, LaunchRecap.pct(5, 0))
        assertEquals(50, LaunchRecap.pct(5, 10))
        assertEquals(33, LaunchRecap.pct(1, 3))
        assertEquals(100, LaunchRecap.pct(7, 7))
    }

    @Test
    fun `위시 파싱 성공률은 성공+실패가 0이면 0이다`() {
        assertEquals(0, wish(ready = 0, failed = 0).parseSuccessRate)
        assertEquals(80, wish(ready = 8, failed = 2).parseSuccessRate)
    }

    @Test
    fun `토너먼트 평균 참가자는 생성이 0이면 0, 아니면 소수 1자리다`() {
        assertEquals("0", tournament(created = 0, participants = 0).avgParticipants)
        assertEquals("3.5", tournament(created = 2, participants = 7).avgParticipants)
    }

    @Test
    fun `D1 리텐션율은 런칭날 가입이 0이면 0이다`() {
        assertEquals(0, retention(signups = 0, returned = 0).d1Rate)
        assertEquals(44, retention(signups = 100, returned = 44).d1Rate)
    }

    @Test
    fun `푸시 CTR 근사는 알림이 0이면 0이다`() {
        assertEquals(0, push(total = 0, read = 0).ctrApproxPct)
        assertEquals(31, push(total = 100, read = 31).ctrApproxPct)
    }

    private fun wish(
        ready: Long,
        failed: Long,
    ) = LaunchRecap.Wish(total = ready + failed, fromUrl = 0, fromImage = 0, parsedReady = ready, parsedFailed = failed)

    private fun tournament(
        created: Long,
        participants: Long,
    ) = LaunchRecap.Tournament(created = created, participants = participants, itemsAdded = 0, completed = 0, plays = 0)

    private fun retention(
        signups: Long,
        returned: Long,
    ) = LaunchRecap.Retention(launchDaySignups = signups, d1Returned = returned, dau = emptyList())

    private fun push(
        total: Long,
        read: Long,
    ) = LaunchRecap.Push(byType = emptyMap(), deliverySuccess = 0, deliveryFailure = 0, deliverySkipped = 0, notificationsTotal = total, readApprox = read)
}
