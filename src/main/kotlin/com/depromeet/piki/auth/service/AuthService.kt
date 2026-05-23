package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.exception.AuthException
import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.piki.auth.service.dto.SignupResult
import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.service.UserService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun createGuest(): SignupResult {
        val user = userService.createGuest()
        val tokenPair = issueTokenPair(user)
        return SignupResult(tokenPair = tokenPair, user = user)
    }

    fun createMember(nickname: String): SignupResult {
        val user = userService.createMember(nickname)
        val tokenPair = issueTokenPair(user)
        return SignupResult(tokenPair = tokenPair, user = user)
    }

    fun refresh(refreshToken: String): TokenPair {
        val userId = jwtProvider.parseRefreshToken(refreshToken) ?: throw AuthException.invalidToken()
        val user = userService.findById(userId)
        user.deletedAt?.let { throw AuthException.invalidToken() }
        if (!refreshTokenStore.consumeIfMatches(userId, refreshToken)) throw AuthException.invalidToken()
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
