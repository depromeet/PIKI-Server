package com.depromeet.piki.support

import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class StubRefreshTokenStore : RefreshTokenStore {
    private val store = ConcurrentHashMap<UUID, String>()

    // default: 정상 저장. 테스트에서 onSave = { _, _ -> throw ... } 로 Redis 장애 시뮬레이션.
    var onSave: (UUID, String) -> Unit = { userId, token -> store[userId] = token }

    override fun save(userId: UUID, refreshToken: String) = onSave(userId, refreshToken)

    override fun get(userId: UUID): String? = store[userId]

    override fun delete(userId: UUID) {
        store.remove(userId)
    }

    // 매치·불일치 모두 키 삭제 — 불일치는 family invalidation (RedisRefreshTokenStore Lua 스크립트와 동일 의미)
    override fun consumeIfMatches(userId: UUID, token: String): Boolean {
        val stored = store.remove(userId) ?: return false
        return stored == token
    }

    fun reset() {
        store.clear()
        onSave = { userId, token -> store[userId] = token }
    }
}
