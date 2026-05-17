package com.depromeet.team3.auth.infrastructure.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(userId: UUID): String =
        buildToken(userId, TokenType.ACCESS, jwtProperties.accessTokenExpirySeconds)

    fun generateRefreshToken(userId: UUID): String =
        buildToken(userId, TokenType.REFRESH, jwtProperties.refreshTokenExpirySeconds)

    fun parseAccessToken(token: String): UUID? = parseToken(token, TokenType.ACCESS)

    fun parseRefreshToken(token: String): UUID? = parseToken(token, TokenType.REFRESH)

    private fun buildToken(
        userId: UUID,
        type: TokenType,
        expirySeconds: Long,
    ): String {
        val now = Date()
        return Jwts
            .builder()
            .subject(userId.toString())
            .claim(CLAIM_TYPE, type.claimValue)
            .issuedAt(now)
            .expiration(Date(now.time + expirySeconds * MILLIS_PER_SECOND))
            .signWith(secretKey)
            .compact()
    }

    // 서명 검증 + 만료 검증 + token type 일치 검증을 한 번에 수행한다.
    // 어느 단계든 실패하면 null 을 반환하여 호출자가 Elvis 로 분기할 수 있다.
    private fun parseToken(
        token: String,
        expected: TokenType,
    ): UUID? =
        runCatching {
            val claims = parseClaims(token)
            val actualType = TokenType.fromClaim(claims[CLAIM_TYPE] as? String)
            check(actualType == expected) {
                "JWT type mismatch: expected=${expected.claimValue}, actual=${actualType?.claimValue}"
            }
            UUID.fromString(claims.subject)
        }.onFailure { logger.debug("JWT 파싱 실패 (expected={}): {}", expected, it.message) }
            .getOrNull()

    private fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    companion object {
        private const val CLAIM_TYPE = "type"
        private const val MILLIS_PER_SECOND = 1_000L
        private val logger = LoggerFactory.getLogger(JwtProvider::class.java)
    }
}
