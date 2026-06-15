package com.depromeet.piki.admin.access

import com.depromeet.piki.admin.config.AdminProperties
import com.depromeet.piki.admin.config.ClientIp
import com.depromeet.piki.admin.config.ConditionalOnAdminEnabled
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// prod·전 환경의 /admin 게이트 — 슬랙으로 검증된 세션 + allowlist IP + 세션-IP 바인딩 셋 다 맞아야 통과, 아니면 404.
// password 가 아니라 "슬랙 링크 클릭으로 발급된 세션"이 신원이다. 미허용은 401/302 가 아니라 404(존재 숨김).
// 공개 진입(/admin-access/**)·정적(/admin-assets/**)은 경로가 달라 이 필터 대상이 아니다(shouldNotFilter).
//
// order: 관측·TraceIdHeader(HIGHEST+1,+2) 안쪽(+4)이라 traceId 가 차 있고, Security(-100)보다 바깥이라
// 메인 JWT 체인에 닿기 전에 끊는다. localBypass(로컬 개발)면 게이트를 건너뛴다.
@Component
@ConditionalOnAdminEnabled
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
class AdminAccessFilter(
    private val allowlistService: AdminAllowlistService,
    private val adminProperties: AdminProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (adminProperties.localBypass) {
            filterChain.doFilter(request, response)
            return
        }
        val ip = ClientIp.of(request)
        val session = request.getSession(false) ?: return deny(response)
        if (!AdminSession.hasIdentity(session)) return deny(response)
        if (AdminSession.boundIp(session) != ip) return deny(response)
        if (!allowlistService.isAllowed(ip)) return deny(response)
        allowlistService.refresh(ip) // sliding
        filterChain.doFilter(request, response)
    }

    // /admin 과 /admin/** 만 게이트. /admin-assets·/admin-access 는 다른 prefix 라 제외(공개 진입·정적).
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return !(uri == "/admin" || uri.startsWith("/admin/"))
    }

    // setStatus 로 막는다(sendError 금지) — sendError 는 /error 로 ERROR 디스패치를 일으켜 메인 체인이 401 로
    // 가로채면 "존재 숨김(404)" 의도가 깨진다. 체인을 더 진행하지 않으니 빈 404 로 응답이 닫힌다.
    private fun deny(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_NOT_FOUND
    }
}
