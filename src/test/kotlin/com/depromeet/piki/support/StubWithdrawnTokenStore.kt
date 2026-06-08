package com.depromeet.piki.support

import com.depromeet.piki.auth.infrastructure.redis.WithdrawnTokenStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// 통합 테스트용 in-memory WithdrawnTokenStore (실제 Redis 격리). RedisRefreshTokenStore 처럼
// 실제 Redis 구현은 RedisWithdrawnTokenStoreIntegrationTest 에서 별도로 검증한다.
class StubWithdrawnTokenStore : WithdrawnTokenStore {
    private val withdrawn = ConcurrentHashMap.newKeySet<UUID>()

    override fun markWithdrawn(userId: UUID) {
        withdrawn.add(userId)
    }

    override fun isWithdrawn(userId: UUID): Boolean = userId in withdrawn

    fun reset() {
        withdrawn.clear()
    }
}
