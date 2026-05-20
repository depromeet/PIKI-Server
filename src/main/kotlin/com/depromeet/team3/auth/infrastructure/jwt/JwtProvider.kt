package com.depromeet.team3.auth.infrastructure.jwt

import com.depromeet.team3.user.domain.IdentityType
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

private const val CLAIM_TYPE = "type"
private const val CLAIM_ROLE = "role"
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

    fun generateAccessToken(
        userId: UUID,
        identityType: IdentityType,
    ): String =
        buildToken(
            userId = userId,
            type = TokenType.ACCESS,
            expirySeconds = jwtProperties.accessTokenExpirySeconds,
            role = identityType.name,
        )

    fun generateRefreshToken(userId: UUID): String =
        buildToken(
            userId = userId,
            type = TokenType.REFRESH,
            expirySeconds = jwtProperties.refreshTokenExpirySeconds,
            role = null,
        )

    fun parseAccessToken(token: String): AccessTokenPayload? =
        runCatching {
            val claims = parseClaims(token)
            val rawType = claims[CLAIM_TYPE] as? String
            val actualType = TokenType.fromClaim(rawType)
            check(actualType == TokenType.ACCESS) {
                "JWT type mismatch: expected=access, actual=${rawType ?: "<missing>"}"
            }
            val rawRole = claims[CLAIM_ROLE] as? String ?: error("role claim missing in access token")
            AccessTokenPayload(
                userId = UUID.fromString(claims.subject),
                identityType = IdentityType.valueOf(rawRole),
            )
        }.onFailure { logger.info("ACCESS JWT 파싱 실패: {}", it.message) }
            .getOrNull()

    fun parseRefreshToken(token: String): UUID? =
        runCatching {
            val claims = parseClaims(token)
            val rawType = claims[CLAIM_TYPE] as? String
            val actualType = TokenType.fromClaim(rawType)
            check(actualType == TokenType.REFRESH) {
                "JWT type mismatch: expected=refresh, actual=${rawType ?: "<missing>"}"
            }
            UUID.fromString(claims.subject)
        }.onFailure { logger.info("REFRESH JWT 파싱 실패: {}", it.message) }
            .getOrNull()

    fun validateToken(token: String): Boolean = runCatching { parseClaims(token) }.isSuccess

    private fun buildToken(
        userId: UUID,
        type: TokenType,
        expirySeconds: Long,
        role: String?,
    ): String {
        val now = Date()
        return Jwts
            .builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim(CLAIM_TYPE, type.claimValue)
            .issuedAt(now)
            .expiration(Date(now.time + expirySeconds * MILLIS_PER_SECOND))
            .apply { role?.let { claim(CLAIM_ROLE, it) } }
            .signWith(secretKey)
            .compact()
    }

    private fun parseClaims(token: String): Claims =
        Jwts
            .parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload

    data class AccessTokenPayload(
        val userId: UUID,
        val identityType: IdentityType,
    )
}
