package com.depromeet.piki.auth.filter

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.auth.infrastructure.redis.WithdrawnTokenStore
import com.depromeet.piki.auth.web.TokenCookieWriter
import com.depromeet.piki.common.logging.LoggingKeys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
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
                // 인증된 요청의 userId 를 MDC 에 실어 이 요청 이후 전 로그(컨트롤러·서비스·async 워커)가 같은
                // userId 를 단다 — traceId 가 요청 1건을 묶고, userId 가 그 유저의 여러 요청을 가로질러 묶는다.
                // async 전파는 ContextPropagatingTaskDecorator(AsyncConfig)가 MDC 를 워커로 넘겨 보장한다.
                MDC.put(LoggingKeys.USER_ID, payload.userId.toString())
            }
        try {
            filterChain.doFilter(request, response)
        } finally {
            // 톰캣 워커 스레드는 풀에서 재사용되므로 요청 끝에 반드시 지운다 — 안 지우면 다음 요청(미인증 포함)이
            // 이전 요청의 userId 를 물려받아 오염된다. put 안 한 경우에도 remove 는 무해(no-op).
            MDC.remove(LoggingKeys.USER_ID)
        }
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
