package com.depromeet.piki.common.web

import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 주 경로(Tracer 의 현재 Span 에서 traceId 를 읽어 헤더에 싣기)는 실제 trace 스코프가 필요해
// TraceIdResponseIntegrationTest 가 검증한다. 여기서는 현재 Span 이 없을 때의 MDC 폴백·미설정 분기를 단위로 본다.
class TraceIdHeaderFilterTest {
    // 현재 Span 이 없는 Tracer(NOOP) — currentTraceId() 가 MDC 폴백 경로로 떨어지게 한다.
    private val filter = TraceIdHeaderFilter(Tracer.NOOP)

    // MDC 는 스레드로컬 전역이라 테스트 간 누수를 차단한다 (각 테스트는 필요한 상태를 본문에서 명시 set).
    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `현재 Span 이 없으면 MDC traceId 로 폴백해 X-Trace-Id 헤더에 싣는다`() {
        MDC.put("traceId", "65b2e1f0c3a94d77")
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest(), response, MockFilterChain())

        assertEquals("65b2e1f0c3a94d77", response.getHeader(TraceIdHeaderFilter.HEADER))
    }

    @Test
    fun `현재 Span 도 MDC 도 비어 있으면 X-Trace-Id 헤더를 달지 않는다`() {
        MDC.remove("traceId")
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest(), response, MockFilterChain())

        assertNull(response.getHeader(TraceIdHeaderFilter.HEADER))
    }
}
