package com.depromeet.piki.auth.service

import com.depromeet.piki.auth.exception.AuthException
import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.RefreshTokenStore
import com.depromeet.piki.auth.service.dto.SignupResult
import com.depromeet.piki.auth.service.dto.TokenPair
import com.depromeet.piki.common.logging.SensitiveData
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
        log.info("게스트 생성 userId={}", user.id)
        return SignupResult(tokenPair = tokenPair, user = user)
    }

    fun createMember(nickname: String): SignupResult {
        // 닉네임 원문은 PII 라 싣지 않는다 — 생성 사실과 userId 만.
        val user = userService.createMember(nickname)
        val tokenPair = issueTokenPair(user)
        log.info("회원 생성 userId={}", user.id)
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

    // 갱신 거부는 모두 클라이언트 계약 위반(만료·위조·재사용 토큰)이라 info 로 사유를 구분해 남긴다 —
    // prod 401 디버깅의 핵심: "왜 거부됐나"(파싱 실패/탈퇴/저장 토큰 불일치)를 traceId·userId 와 함께 본다.
    // refresh 토큰 원문은 크리덴셜이라 지문(maskToken)으로만 찍는다.
    fun refresh(refreshToken: String): TokenPair {
        val userId =
            jwtProvider.parseRefreshToken(refreshToken) ?: run {
                log.info("토큰 갱신 거부 사유=refresh 토큰 파싱 실패(만료·위조) token={}", SensitiveData.maskToken(refreshToken))
                throw AuthException.invalidToken()
            }
        val user = userService.findById(userId)
        user.deletedAt?.let {
            log.info("토큰 갱신 거부 사유=탈퇴 유저 userId={}", userId)
            throw AuthException.invalidToken()
        }
        if (!refreshTokenStore.consumeIfMatches(userId, refreshToken)) {
            log.info("토큰 갱신 거부 사유=저장된 refresh 토큰 불일치(재사용·회전 후) userId={}", userId)
            throw AuthException.invalidToken()
        }
        log.info("토큰 갱신 성공 userId={}", userId)
        return issueTokenPair(user)
    }

    fun logout(userId: UUID) {
        refreshTokenStore.delete(userId)
        log.info("로그아웃 userId={}", userId)
    }

    fun createTokensForUser(user: User): TokenPair {
        user.deletedAt?.let { throw AuthException.invalidToken() }
        return issueTokenPair(user)
    }

    private fun issueTokenPair(user: User): TokenPair {
        val accessToken = jwtProvider.generateAccessToken(user.id, user.identityType)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenStore.save(user.id, refreshToken)
        return TokenPair(accessToken = accessToken, refreshToken = refreshToken)
    }
}
