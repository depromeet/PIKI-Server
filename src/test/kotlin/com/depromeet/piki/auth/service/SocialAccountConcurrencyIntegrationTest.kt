package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.infrastructure.oauth.OAuthProvider
import com.depromeet.piki.auth.infrastructure.oauth.OAuthUserInfo
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.user.repository.UserDetailRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 동시 첫 로그인 race 검증. @Transactional 자동 롤백을 쓰지 않는다 — 별도 트랜잭션 동시 진행이
// 시뮬레이션의 본질이라(REQUIRED mutation 이 각자 커밋돼야 충돌이 재현된다), 격리된 socialId 를 쓰고
// 끝에서 만든 행을 직접 정리한다. (CLAUDE.md "동시성·시간 의존 통합 테스트" 분류)
class SocialAccountConcurrencyIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var socialAccountService: SocialAccountService

    @Autowired
    private lateinit var userDetailRepository: UserDetailRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `같은 소셜로 동시 첫 로그인해도 한 user 로 합류하고 500 이 터지지 않는다`() {
        val socialId = "concurrent-${UUID.randomUUID()}"
        val userInfo = OAuthUserInfo(OAuthProvider.GOOGLE, socialId, null)
        val threadCount = 4
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val resolvedIds = Collections.synchronizedList(mutableListOf<UUID>())
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        try {
            repeat(threadCount) {
                pool.submit {
                    ready.countDown()
                    start.await()
                    runCatching { socialAccountService.resolveUser(userInfo, null).id }
                        .onSuccess { resolvedIds.add(it) }
                        .onFailure { errors.add(it) }
                }
            }
            // 모든 스레드가 시작 게이트에 도달했는지 강제 검증 — 안 하면 느린 CI 에서 일부가 준비 전에
            // start 가 풀려 동시성이 약해지고 race 가 거짓양성으로 통과할 수 있다.
            assertTrue(ready.await(5, TimeUnit.SECONDS), "모든 스레드가 시작 게이트에 준비돼야 한다")
            start.countDown() // 동시 출발
            pool.shutdown()
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS), "동시 작업이 시간 내 끝나야 한다")

            assertEquals(emptyList(), errors.map { it.message }, "동시 첫 로그인에서 예외(500)가 나면 안 된다")
            assertEquals(threadCount, resolvedIds.size, "모든 요청이 user 를 받아야 한다")
            assertEquals(1, resolvedIds.toSet().size, "모든 동시 요청이 같은 user 로 합류해야 한다")

            val linked = userDetailRepository.findByProviderAndSocialId(OAuthProvider.GOOGLE.name, socialId)
            assertNotNull(linked, "소셜 연결이 정확히 하나 남아야 한다")
            assertEquals(resolvedIds.first(), linked.getIdOrNull(), "합류된 user 가 연결의 주인과 같아야 한다")
        } finally {
            // @Transactional 자동 롤백이 없으므로 만든 행을 직접 정리 (social_id 문자열로 삭제 → UUID 바이너리 바인딩 회피)
            jdbcTemplate.update("DELETE FROM users WHERE id IN (SELECT user_id FROM user_details WHERE social_id = ?)", socialId)
            jdbcTemplate.update("DELETE FROM user_details WHERE social_id = ?", socialId)
        }
    }
}
