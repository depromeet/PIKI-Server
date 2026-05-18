package com.depromeet.team3.auth.infrastructure.jwt

import com.depromeet.team3.user.domain.IdentityType
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
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

    fun generateAccessToken(
        userId: UUID,
        identityType: IdentityType,
    ): String = buildToken(userId, jwtProperties.accessTokenExpirySeconds, TOKEN_TYPE_ACCESS, identityType.name)

    fun generateRefreshToken(userId: UUID): String =
        buildToken(userId, jwtProperties.refreshTokenExpirySeconds, TOKEN_TYPE_REFRESH, null)

    fun getUserIdFromToken(token: String): UUID = UUID.fromString(parseClaims(token).subject)

    fun getIdentityTypeFromToken(token: String): IdentityType =
        IdentityType.valueOf(parseClaims(token)[CLAIM_ROLE] as String)

    fun isAccessToken(token: String): Boolean =
        runCatching {
            parseClaims(token)[CLAIM_TYPE] == TOKEN_TYPE_ACCESS
        }.getOrDefault(false)

    fun validateToken(token: String): Boolean = runCatching { parseClaims(token) }.isSuccess

    fun parseAccessToken(token: String): AccessTokenPayload? =
        runCatching {
            val claims = parseClaims(token)
            if (claims[CLAIM_TYPE] != TOKEN_TYPE_ACCESS) return null
            AccessTokenPayload(
                userId = UUID.fromString(claims.subject),
                identityType = IdentityType.valueOf(claims[CLAIM_ROLE] as String),
            )
        }.getOrNull()

    private fun buildToken(
        userId: UUID,
        expirySeconds: Long,
        type: String,
        role: String?,
    ): String {
        val now = Date()
        return Jwts
            .builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + expirySeconds * 1_000))
            .claim(CLAIM_TYPE, type)
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

    companion object {
        private const val CLAIM_TYPE = "type"
        private const val CLAIM_ROLE = "role"
        const val TOKEN_TYPE_ACCESS = "ACCESS"
        const val TOKEN_TYPE_REFRESH = "REFRESH"
    }

    data class AccessTokenPayload(
        val userId: UUID,
        val identityType: IdentityType,
    )
}
