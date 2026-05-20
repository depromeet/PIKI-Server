package com.depromeet.team3.auth.infrastructure.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

private const val CLAIM_TYPE = "type"
private const val MILLIS_PER_SECOND = 1_000L
private val logger = LoggerFactory.getLogger(JwtProvider::class.java)

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
) {
    // 부팅 시점에 즉시 생성한다. lazy 로 두면 첫 토큰 발급/검증 트래픽까지 키 유효성 문제가
    // 가려져 운영 사고의 표면이 트래픽 시점으로 미뤄진다. JwtProperties 의 @Size(min=32) 와
    // 합쳐 부팅 시점 fail-fast 보장.
    private val secretKey: SecretKey =
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))

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
            val rawType = claims[CLAIM_TYPE] as? String
            val actualType = TokenType.fromClaim(rawType)
            check(actualType == expected) {
                "JWT type mismatch: expected=${expected.claimValue}, actual=${rawType ?: "<missing>"}"
            }
            UUID.fromString(claims.subject)
        }.onFailure { logger.debug("JWT 파싱 실패 (expected={}): {}", expected.claimValue, it.message) }
            .getOrNull()

    private fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

}
