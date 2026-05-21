package com.depromeet.team3.auth.infrastructure.redis

import java.util.UUID

interface RefreshTokenStore {
    fun save(
        userId: UUID,
        refreshToken: String,
    )

    fun get(userId: UUID): String?

    fun delete(userId: UUID)

    // 저장된 토큰과 일치하면 삭제 후 true, 불일치 또는 없으면 false 반환.
    // GET + 비교 + DEL 을 Lua 스크립트로 원자적으로 수행해 TOCTOU 경쟁 조건을 차단한다.
    fun consumeIfMatches(
        userId: UUID,
        token: String,
    ): Boolean
}
