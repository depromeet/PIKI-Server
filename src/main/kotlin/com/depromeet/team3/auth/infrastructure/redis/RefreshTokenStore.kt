package com.depromeet.team3.auth.infrastructure.redis

import java.util.UUID

interface RefreshTokenStore {
    fun save(
        userId: UUID,
        refreshToken: String,
    )

    fun get(userId: UUID): String?

    fun delete(userId: UUID)
}
