package com.depromeet.piki.auth.filter

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.WithdrawnTokenStore
import com.depromeet.piki.auth.web.TokenCookieWriter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val withdrawnTokenStore: WithdrawnTokenStore,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        extractToken(request)
            ?.let { jwtProvider.parseAccessToken(it) }
            // 탈퇴 회원의 access token 거부 — stateless JWT 라 만료 전까진 유효하므로 denylist 로 즉시 차단한다.
            // 인증 토큰이 있는 요청에서만 Redis 를 1회 조회한다(미인증 요청은 영향 없음).
            ?.takeUnless { withdrawnTokenStore.isWithdrawn(it.userId) }
            ?.let { payload ->
                val authority = SimpleGrantedAuthority(payload.identityType.name)
                SecurityContextHolder.getContext().authentication =
                    PreAuthenticatedAuthenticationToken(payload.userId, null, listOf(authority))
            }
        filterChain.doFilter(request, response)
    }

    // 헤더(APP) 우선 → 없으면 access_token 쿠키(WEB). 둘 다 있으면 헤더가 이긴다.
    private fun extractToken(request: HttpServletRequest): String? = extractFromHeader(request) ?: extractFromCookie(request)

    private fun extractFromHeader(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX)) return null
        return header.substring(BEARER_PREFIX.length)
    }

    private fun extractFromCookie(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == TokenCookieWriter.ACCESS_COOKIE }?.value

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
