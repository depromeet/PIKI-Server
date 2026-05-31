package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.exception.AuthException
import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.piki.auth.service.dto.SignupResult
import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.domain.UserException
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

    // Dev 전용. 기존 user 의 token pair 를 발급해 임의 user 시나리오를 재현할 수 있게 한다.
    // OAuth 통합 (epic #122) 전까지의 임시 endpoint 와 같은 결로 묶여 운영 노출 차단 예정 (#177 후속).
    fun issueTokenForExistingUser(userId: UUID): SignupResult {
        val user = userService.findById(userId)
        user.deletedAt?.let { throw UserException.deletedUser(userId) }
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

    // 이미 확정된 user 에게 토큰 발급. 소셜 로그인(OAuthLoginService)이 resolve 한 user 로 재사용한다.
    fun issueTokensFor(user: User): SignupResult = SignupResult(tokenPair = issueTokenPair(user), user = user)

    private fun issueTokenPair(user: User): TokenPair {
        val accessToken = jwtProvider.generateAccessToken(user.id, user.identityType)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenStore.save(user.id, refreshToken)
        return TokenPair(accessToken = accessToken, refreshToken = refreshToken)
    }
}
