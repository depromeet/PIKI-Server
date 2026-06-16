package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.support.IntegrationTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// FCM 토큰 등록 동시성 검증(#396) — 같은 토큰으로 동시 등록 시 check-then-act 가 UNIQUE 충돌(500)을 내던 것을
// retry-upsert 로 흡수하는지 본다. @Transactional 자동 롤백을 쓰지 않는다 — 별도 트랜잭션 동시 진행(각 register
// 시도가 독립 커밋돼야 충돌이 재현)이 시뮬레이션의 본질이라, 격리된 token/deviceId 를 쓰고 끝에서 만든 행을
// 직접 정리한다. (CLAUDE.md "동시성·시간 의존 통합 테스트" 분류)
class UserDeviceRegisterConcurrencyIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var userDeviceService: UserDeviceService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun countByToken(token: String): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_devices WHERE fcm_token = ?", Int::class.java, token) ?: 0

    @Test
    fun `같은 유저 기기 토큰으로 동시 등록해도 500 없이 기기 1개로 수렴한다`() {
        val token = "concurrent-${UUID.randomUUID()}"
        val userId = UUID.randomUUID()
        val deviceId = "device-${UUID.randomUUID()}"
        val threadCount = 4
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        try {
            repeat(threadCount) {
                pool.submit {
                    ready.countDown()
                    start.await()
                    runCatching { userDeviceService.register(userId, deviceId, token) }
                        .onFailure { errors.add(it) }
                }
            }
            // 모든 스레드가 시작 게이트에 도달했는지 강제 검증 — 안 하면 느린 CI 에서 일부가 준비 전에 start 가
            // 풀려 동시성이 약해지고 race 가 거짓양성으로 통과할 수 있다.
            assertTrue(ready.await(5, TimeUnit.SECONDS), "모든 스레드가 시작 게이트에 준비돼야 한다")
            start.countDown() // 동시 출발
            pool.shutdown()
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "동시 작업이 시간 내 끝나야 한다")

            assertEquals(emptyList(), errors.map { it.message }, "동시 등록에서 예외(500)가 나면 안 된다")
            assertEquals(1, countByToken(token), "동시 등록이 기기 1 row 로 수렴해야 한다")
        } finally {
            jdbcTemplate.update("DELETE FROM user_devices WHERE fcm_token = ?", token)
        }
    }
}

// 참고 — "서로 다른 유저가 같은 토큰을 동시 등록"하는 race 는 일부러 검증하지 않는다. FCM 토큰은 앱 설치(기기)
// 하나당 전역 유일이라, 서로 다른 유저가 동일 토큰을 같은 순간에 등록하는 상황 자체가 현실에 없다(#396 이 노리는
// race 는 같은 클라이언트의 중복 등록 = 위 테스트). 그 인위적 시나리오는 토큰 보유자 재배정(delete+insert)이
// N-way 로 경합해 1회 재시도로는 못 푸는 별개의 어려운 문제라, 이슈 범위 밖으로 둔다.
