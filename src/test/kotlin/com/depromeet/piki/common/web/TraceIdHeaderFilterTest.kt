package com.depromeet.piki.common.web

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

// TraceIdHeaderFilter 가 요청 스코프의 MDC traceId 를 X-Trace-Id 응답 헤더로 옮기는 로직의 단위 검증.
// 운영에선 ServerHttpObservationFilter 가 MDC 를 채우고, 이 필터가 그 값을 헤더로 노출한다.
class TraceIdHeaderFilterTest {
    // MDC 는 스레드로컬 전역이라 테스트 간 누수를 차단한다 (각 테스트는 필요한 상태를 본문에서 명시 set).
    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `MDC 에 traceId 가 있으면 X-Trace-Id 응답 헤더에 싣는다`() {
        MDC.put("traceId", "65b2e1f0c3a94d77")
        val response = MockHttpServletResponse()

        TraceIdHeaderFilter().doFilter(MockHttpServletRequest(), response, MockFilterChain())

        assertEquals("65b2e1f0c3a94d77", response.getHeader(TraceIdHeaderFilter.HEADER))
    }

    @Test
    fun `MDC 에 traceId 가 없으면 X-Trace-Id 헤더를 달지 않는다`() {
        MDC.remove("traceId")
        val response = MockHttpServletResponse()

        TraceIdHeaderFilter().doFilter(MockHttpServletRequest(), response, MockFilterChain())

        assertNull(response.getHeader(TraceIdHeaderFilter.HEADER))
    }
}
