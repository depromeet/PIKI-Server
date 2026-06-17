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

// dev/staging 도메인 전체를 allowlist IP 로만 열어 내부 환경을 private 로 만든다(공개 차단). prod 는 off(공개 — 실유저용).
// staging 이 prod 프로파일을 공유하므로(deploy.yml) 프로파일이 아니라 env 플래그(admin.environment-gate)로 켠다 —
// 배포가 dev/staging 에만 ENV_ACCESS_GATE=true 를 준다. AdminAccessFilter 와 같은 allowlist·슬랙 등록 흐름을 공유한다.
//
// 항상 통과: 슬랙 grant 진입(/admin-access/**, 없으면 IP 등록 자체가 막히는 닭/달걀)·health·actuator·localhost
// (배포 health curl, EC2 내부 Grafana Alloy scrape). 모바일 테스터·로컬 기기는 자기 IP 를 슬랙으로 등록한다(24h sliding).
@Component
@ConditionalOnAdminEnabled
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class EnvironmentAccessFilter(
    private val allowlistService: AdminAllowlistService,
    private val adminProperties: AdminProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ip = ClientIp.of(request)
        if (isLocalhost(ip) || allowlistService.isAllowed(ip)) {
            allowlistService.refresh(ip) // sliding (localhost 는 키가 없어 no-op)
            filterChain.doFilter(request, response)
            return
        }
        // setStatus 로 막는다(sendError 금지) — sendError 의 /error 디스패치가 메인 체인에서 401 로 바뀌는 걸 피한다.
        response.status = HttpServletResponse.SC_NOT_FOUND
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!adminProperties.environmentGate) return true // prod·로컬: 도메인 게이트 off
        val uri = request.requestURI
        // /admin-access(슬랙 진입, 없으면 IP 등록 자체가 막힘)·/health(라이브니스)만 무조건 통과시킨다.
        // /actuator 는 필터를 '거치게' 두고 doFilterInternal 의 isLocalhost 로 EC2 내부 Grafana Alloy scrape 만 허용한다 —
        // shouldNotFilter 로 통째 우회하면 nginx 차단이 흔들리는 순간 외부 IP 에서 관리 엔드포인트(/actuator/loggers 등)가 열린다(앱 레벨 2중 방어).
        return uri.startsWith("/admin-access/") || uri == "/health"
    }

    private fun isLocalhost(ip: String): Boolean = ip == "127.0.0.1" || ip == "::1" || ip == "0:0:0:0:0:0:0:1"
}
