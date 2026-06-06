package com.depromeet.piki.user.service

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// graceCutoff 는 순수 함수라 Spring/DB 없이 단위 테스트한다.
class DailyAccountPurgeSchedulerTest {
    private val now = LocalDateTime.of(2026, 6, 7, 4, 0, 0)

    @Test
    fun `cutoff 는 now 에서 graceDays 만큼 과거다`() {
        assertEquals(LocalDateTime.of(2026, 5, 8, 4, 0, 0), DailyAccountPurgeScheduler.graceCutoff(now, 30))
    }

    @Test
    fun `31일 전 탈퇴는 cutoff 이전이라 파기 대상이다`() {
        val cutoff = DailyAccountPurgeScheduler.graceCutoff(now, 30)
        val deletedAt = now.minusDays(31)
        assertTrue(deletedAt.isBefore(cutoff))
    }

    @Test
    fun `29일 전 탈퇴는 cutoff 이후라 아직 파기 대상이 아니다`() {
        val cutoff = DailyAccountPurgeScheduler.graceCutoff(now, 30)
        val deletedAt = now.minusDays(29)
        assertTrue(deletedAt.isAfter(cutoff))
    }

    @Test
    fun `정확히 graceDays 전 탈퇴는 cutoff 와 같아 strict less-than 에서 제외된다`() {
        val cutoff = DailyAccountPurgeScheduler.graceCutoff(now, 30)
        val deletedAt = now.minusDays(30)
        // 쿼리는 deleted_at < cutoff 라 경계값(=)은 포함되지 않는다.
        assertEquals(cutoff, deletedAt)
        assertTrue(!deletedAt.isBefore(cutoff))
    }
}
