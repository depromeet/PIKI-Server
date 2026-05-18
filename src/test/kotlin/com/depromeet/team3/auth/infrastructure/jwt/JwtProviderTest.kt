package com.depromeet.team3.auth.infrastructure.jwt

import com.depromeet.team3.user.domain.IdentityType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtProviderTest {
    private val properties =
        JwtProperties(
            secret = "test-secret-key-must-be-at-least-32-chars!",
            accessTokenExpirySeconds = 3600,
            refreshTokenExpirySeconds = 1_209_600,
        )
    private val jwtProvider = JwtProvider(properties)

    @Test
    fun `access token 을 발급하고 userId 를 추출할 수 있다`() {
        val userId = UUID.randomUUID()
        val token = jwtProvider.generateAccessToken(userId, IdentityType.GUEST)
        assertEquals(userId, jwtProvider.getUserIdFromToken(token))
    }

    @Test
    fun `refresh token 을 발급하고 userId 를 추출할 수 있다`() {
        val userId = UUID.randomUUID()
        val token = jwtProvider.generateRefreshToken(userId)
        assertEquals(userId, jwtProvider.getUserIdFromToken(token))
    }

    @Test
    fun `유효한 토큰은 validateToken 이 true 를 반환한다`() {
        val token = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)
        assertTrue(jwtProvider.validateToken(token))
    }

    @Test
    fun `위조된 토큰은 validateToken 이 false 를 반환한다`() {
        assertFalse(jwtProvider.validateToken("invalid.token.value"))
    }

    @Test
    fun `서로 다른 userId 로 발급한 토큰에서 추출한 userId 가 각각 일치한다`() {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val token1 = jwtProvider.generateAccessToken(userId1, IdentityType.GUEST)
        val token2 = jwtProvider.generateAccessToken(userId2, IdentityType.MEMBER)
        assertEquals(userId1, jwtProvider.getUserIdFromToken(token1))
        assertEquals(userId2, jwtProvider.getUserIdFromToken(token2))
    }

    @Test
    fun `access token 에서 identityType 을 추출할 수 있다`() {
        val token = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.MEMBER)
        assertEquals(IdentityType.MEMBER, jwtProvider.getIdentityTypeFromToken(token))
    }

    @Test
    fun `access token 은 isAccessToken 이 true 를 반환한다`() {
        val token = jwtProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)
        assertTrue(jwtProvider.isAccessToken(token))
    }

    @Test
    fun `refresh token 은 isAccessToken 이 false 를 반환한다`() {
        val token = jwtProvider.generateRefreshToken(UUID.randomUUID())
        assertFalse(jwtProvider.isAccessToken(token))
    }

    @Test
    fun `만료된 토큰은 validateToken 이 false 를 반환한다`() {
        val expiredProvider =
            JwtProvider(
                JwtProperties(
                    secret = "test-secret-key-must-be-at-least-32-chars!",
                    accessTokenExpirySeconds = -1,
                    refreshTokenExpirySeconds = -1,
                ),
            )
        val token = expiredProvider.generateAccessToken(UUID.randomUUID(), IdentityType.GUEST)
        assertFalse(jwtProvider.validateToken(token))
    }
}
