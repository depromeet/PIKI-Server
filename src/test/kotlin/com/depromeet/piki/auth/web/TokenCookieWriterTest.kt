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
                refreshTokenGrace = Duration.ofSeconds(10),
            ),
            AuthCookieProperties(secure = secure),
        )

    @Test
    fun `access_token 쿠키는 정책대로 HttpOnly·SameSite=Lax·Path 루트·만료시간을 갖는다`() {
        val access = writer().setCookies(TokenPair("at", "rt")).first { it.name == "access_token" }

        assertEquals("at", access.value)
        assertTrue(access.isHttpOnly)
        assertTrue(access.isSecure)
        assertEquals("Lax", access.sameSite)
        assertEquals("/", access.path)
        assertEquals(accessExpiry, access.maxAge)
    }

    @Test
    fun `refresh_token 쿠키는 Path 가 루트로 넓혀지고 refresh 만료시간을 갖는다`() {
        // #512: 엣지 proxy 가 페이지 네비게이션에서 refresh_token 을 읽어야 해 Path 를 / 로 넓혔다.
        // 값을 가진(만료 아님) refresh_token 만 골라야 전환기 cleanup 쿠키(빈 값)와 안 섞인다.
        val refresh = writer().setCookies(TokenPair("at", "rt")).first { it.name == "refresh_token" && it.value.isNotEmpty() }

        assertEquals("rt", refresh.value)
        assertEquals("/", refresh.path)
        assertEquals(refreshExpiry, refresh.maxAge)
    }

    @Test
    fun `setCookies 는 옛 Path=api_v1_auth refresh_token 쿠키를 함께 만료시킨다 (전환기 cleanup)`() {
        // #512 전환기: 옛 경로 쿠키가 새 Path=/ 쿠키와 공존하면 회전 토큰 불일치로 갱신이 실패하므로,
        // 토큰 발급 때마다 옛 경로 쿠키를 Max-Age=0 으로 지운다.
        val legacy =
            writer()
                .setCookies(TokenPair("at", "rt"))
                .first { it.name == "refresh_token" && it.path == "/api/v1/auth" }

        assertTrue(legacy.value.isEmpty())
        assertTrue(legacy.maxAge.isZero)
    }

    @Test
    fun `secure=false 면 Secure 속성이 꺼진다 (로컬 HTTP)`() {
        val cookies = writer(secure = false).setCookies(TokenPair("at", "rt"))

        assertTrue(cookies.none { it.isSecure })
    }

    @Test
    fun `clearCookies 는 모든 쿠키를 빈 값·Max-Age 0 으로 만료시키고 refresh 는 새 경로와 옛 경로를 모두 지운다`() {
        val cleared = writer().clearCookies()

        assertTrue(cleared.all { it.value.isEmpty() && it.maxAge.isZero })
        assertEquals("/", cleared.first { it.name == "access_token" }.path)
        // #512: 로그아웃은 새 Path=/ 와 옛 Path=/api/v1/auth refresh_token 을 둘 다 만료시켜야 한다
        // (옛 쿠키를 안 지우면 만료까지 잔존해 갱신 경로에서 회전 토큰 불일치를 일으킨다).
        val refreshPaths = cleared.filter { it.name == "refresh_token" }.map { it.path }.toSet()
        assertEquals(setOf("/", "/api/v1/auth"), refreshPaths)
    }
}
