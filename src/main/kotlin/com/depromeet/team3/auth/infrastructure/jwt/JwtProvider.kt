package com.depromeet.team3.auth.infrastructure.jwt

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

    fun generateAccessToken(userId: UUID): String = buildToken(userId, jwtProperties.accessTokenExpirySeconds)

    fun generateRefreshToken(userId: UUID): String = buildToken(userId, jwtProperties.refreshTokenExpirySeconds)

    fun getUserIdFromToken(token: String): UUID = UUID.fromString(parseClaims(token).subject)

    fun validateToken(token: String): Boolean = runCatching { parseClaims(token) }.isSuccess

    private fun buildToken(
        userId: UUID,
        expirySeconds: Long,
    ): String {
        val now = Date()
        return Jwts
            .builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + expirySeconds * 1_000))
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
}
