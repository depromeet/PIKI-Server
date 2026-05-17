package com.depromeet.team3.auth.filter

import com.depromeet.team3.auth.infrastructure.jwt.JwtProperties
import com.depromeet.team3.auth.infrastructure.jwt.JwtProvider
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtAuthenticationFilterTest {
    private val jwtProvider =
        JwtProvider(
            JwtProperties(
                secret = TEST_SECRET,
                accessTokenExpirySeconds = 3600,
                refreshTokenExpirySeconds = 1_209_600,
            ),
        )
    private val filter = JwtAuthenticationFilter(jwtProvider)

    @AfterTest
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `유효한 access token 으로 호출하면 SecurityContext 에 userId 가 principal 로 박힌다`() {
        val userId = UUID.randomUUID()
        val token = jwtProvider.generateAccessToken(userId)
        val request =
            MockHttpServletRequest().apply {
                addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertEquals(userId, SecurityContextHolder.getContext().authentication?.principal)
    }

    @Test
    fun `Authorization 헤더가 없으면 SecurityContext 가 비어 있다`() {
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `Bearer prefix 가 아닌 헤더면 SecurityContext 가 비어 있다`() {
        val request =
            MockHttpServletRequest().apply {
                addHeader(HttpHeaders.AUTHORIZATION, "Token abc123")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `위조된 토큰으로 호출하면 SecurityContext 가 비어 있다`() {
        val request =
            MockHttpServletRequest().apply {
                addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `refresh token 으로 호출하면 type 불일치로 SecurityContext 가 비어 있다`() {
        val refreshToken = jwtProvider.generateRefreshToken(UUID.randomUUID())
        val request =
            MockHttpServletRequest().apply {
                addHeader(HttpHeaders.AUTHORIZATION, "Bearer $refreshToken")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `다른 secret 으로 서명된 토큰은 SecurityContext 가 비어 있다`() {
        val otherProvider =
            JwtProvider(
                JwtProperties(
                    secret = OTHER_SECRET,
                    accessTokenExpirySeconds = 3600,
                    refreshTokenExpirySeconds = 1_209_600,
                ),
            )
        val token = otherProvider.generateAccessToken(UUID.randomUUID())
        val request =
            MockHttpServletRequest().apply {
                addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    companion object {
        private const val TEST_SECRET = "test-secret-key-must-be-at-least-32-chars!"
        private const val OTHER_SECRET = "other-secret-key-must-be-at-least-32-chars!"
    }
}
