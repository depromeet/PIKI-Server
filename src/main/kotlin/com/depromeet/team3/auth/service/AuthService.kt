package com.depromeet.team3.auth.service

import com.depromeet.team3.auth.exception.AuthException
import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import com.depromeet.team3.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.team3.auth.service.dto.TokenPair
import com.depromeet.team3.user.domain.User
import com.depromeet.team3.user.service.UserService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun createGuest(): TokenPair {
        val user = userService.createGuest()
        return issueTokenPair(user)
    }

    fun createMember(nickname: String): TokenPair {
        val user = userService.createMember(nickname)
        return issueTokenPair(user)
    }

    fun refresh(refreshToken: String): TokenPair {
        val userId = jwtProvider.parseRefreshToken(refreshToken) ?: throw AuthException.invalidToken()
        if (!refreshTokenStore.consumeIfMatches(userId, refreshToken)) throw AuthException.invalidToken()
        val user = userService.findById(userId)
        return issueTokenPair(user)
    }

    fun logout(userId: UUID) {
        refreshTokenStore.delete(userId)
    }

    private fun issueTokenPair(user: User): TokenPair {
        val accessToken = jwtProvider.generateAccessToken(user.id, user.identityType)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenStore.save(user.id, refreshToken)
        return TokenPair(accessToken = accessToken, refreshToken = refreshToken)
    }
}
