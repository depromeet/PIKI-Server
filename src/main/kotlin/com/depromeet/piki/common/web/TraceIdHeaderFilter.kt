package com.depromeet.piki.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// Micrometer Tracing(ServerHttpObservationFilter)이 요청 스코프에서 MDC 에 넣어 둔 traceId 를 응답 헤더
// (X-Trace-Id)로 내보낸다. 클라이언트는 받은 X-Trace-Id 로 Grafana Loki 에서 그 요청의 전체 로그를 추적한다.
// traceId 는 응답 body 가 아니라 헤더로만 노출한다 — body 는 순수 데이터로 두고, 추적 메타는 헤더로 분리.
//
// order: ServerHttpObservationFilter 가 HIGHEST_PRECEDENCE+1(최외곽)에서 trace 를 연 뒤라야 MDC 가 차 있으므로
// 그 바로 안쪽(+2)에 둔다. 동시에 Security 필터(DEFAULT_FILTER_ORDER=-100)보다 바깥이라, 401/403 처럼
// DispatcherServlet 에 닿기 전 Security 단에서 나가는 응답에도 헤더가 실린다. chain 진행 전에 헤더를 박아
// 응답이 커밋되기 전에 보장한다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class TraceIdHeaderFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        MDC.get(MDC_TRACE_ID)?.let { response.setHeader(HEADER, it) }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Trace-Id"
        private const val MDC_TRACE_ID = "traceId"
    }
}
