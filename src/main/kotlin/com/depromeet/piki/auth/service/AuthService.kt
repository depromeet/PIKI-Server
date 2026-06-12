package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.exception.AuthException
import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.piki.auth.service.dto.SignupResult
import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.user.domain.User
import com.depromeet.piki.user.domain.UserException
import com.depromeet.piki.user.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthService(
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
    private val refreshTokenStore: RefreshTokenStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
        user.deletedAt?.let { throw UserException.deletedUser() }
        val tokenPair = issueTokenPair(user)
        return SignupResult(tokenPair = tokenPair, user = user)
    }

    // 응답 detail 은 사용자 친화로 통일("로그인 정보가 만료됐어요")돼 어느 단계 실패인지 안 드러나므로,
    // 디버깅·보안 추적용 사유는 던지는 지점에서 로그로 남긴다 (토큰 원문은 노출하지 않는다).
    fun refresh(refreshToken: String): TokenPair {
        val userId =
            jwtProvider.parseRefreshToken(refreshToken)
                ?: run {
                    log.info("refresh 실패: 리프레시 토큰 파싱 불가 (만료·위변조)")
                    throw AuthException.invalidToken()
                }
        val user = userService.findById(userId)
        user.deletedAt?.let {
            log.info("refresh 실패: 탈퇴 유저 userId={}", userId)
            throw AuthException.invalidToken()
        }
        if (!refreshTokenStore.consumeIfMatches(userId, refreshToken)) {
            log.warn("refresh 실패: 리프레시 토큰 매칭 불일치 (재사용·위조 가능) userId={}", userId)
            throw AuthException.invalidToken()
        }
        return issueTokenPair(user)
    }

    fun logout(userId: UUID) {
        refreshTokenStore.delete(userId)
    }

    fun createTokensForUser(user: User): TokenPair {
        user.deletedAt?.let {
            log.info("토큰 발급 거부: 탈퇴 유저 userId={}", user.id)
            throw AuthException.invalidToken()
        }
        return issueTokenPair(user)
    }

    private fun issueTokenPair(user: User): TokenPair {
        val accessToken = jwtProvider.generateAccessToken(user.id, user.identityType)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenStore.save(user.id, refreshToken)
        return TokenPair(accessToken = accessToken, refreshToken = refreshToken)
    }
}
