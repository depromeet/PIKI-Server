package com.depromeet.piki.auth.web

import com.depromeet.piki.auth.infrastructure.jwt.JwtProperties
import com.depromeet.piki.auth.service.dto.TokenPair
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenCookieWriterTest {
    private val accessExpiry = Duration.ofHours(1)
    private val refreshExpiry = Duration.ofDays(14)

    private fun writer(secure: Boolean = true): TokenCookieWriter =
        TokenCookieWriter(
            JwtProperties(
                secret = "x".repeat(32),
                accessTokenExpiry = accessExpiry,
                refreshTokenExpiry = refreshExpiry,
            ),
            AuthCookieProperties(secure = secure),
        )

    @Test
    fun `access_token 쿠키는 정책대로 HttpOnly·SameSite=Strict·Path 루트·만료시간을 갖는다`() {
        val access = writer().setCookies(TokenPair("at", "rt")).first { it.name == "access_token" }

        assertEquals("at", access.value)
        assertTrue(access.isHttpOnly)
        assertTrue(access.isSecure)
        assertEquals("Strict", access.sameSite)
        assertEquals("/", access.path)
        assertEquals(accessExpiry, access.maxAge)
    }

    @Test
    fun `refresh_token 쿠키는 Path 가 api_v1_auth 로 좁혀지고 refresh 만료시간을 갖는다`() {
        val refresh = writer().setCookies(TokenPair("at", "rt")).first { it.name == "refresh_token" }

        assertEquals("rt", refresh.value)
        assertEquals("/api/v1/auth", refresh.path)
        assertEquals(refreshExpiry, refresh.maxAge)
    }

    @Test
    fun `secure=false 면 Secure 속성이 꺼진다 (로컬 HTTP)`() {
        val cookies = writer(secure = false).setCookies(TokenPair("at", "rt"))

        assertTrue(cookies.none { it.isSecure })
    }

    @Test
    fun `clearCookies 는 두 쿠키 모두 빈 값·Max-Age 0 으로 만료시키며 path 는 set 과 동일하다`() {
        val cleared = writer().clearCookies()

        assertTrue(cleared.all { it.value.isEmpty() && it.maxAge.isZero })
        assertEquals("/", cleared.first { it.name == "access_token" }.path)
        assertEquals("/api/v1/auth", cleared.first { it.name == "refresh_token" }.path)
    }
}
