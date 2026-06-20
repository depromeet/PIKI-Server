package com.depromeet.piki.common.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.depromeet.piki.common.logging.LoggingKeys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

// AccessLogFilter 의 로깅 contract 단위 검증 — access log 한 줄에 actor(adminActor)·userId 가 요청 attribute 로부터
// MDC 로 재주입되는지 본다. 통합 경로로는 표현이 어려운 별도 분류다: 공유 컨텍스트가 admin.local-bypass=true 라
// /admin 게이트(actor 를 심는 AdminAccessFilter)가 우회되고, 그 AdminAllowlistService 는 내부 컴포넌트라 stub 으로
// 게이트를 통과시킬 수도 없으며(모킹 금지), access log 의 MDC 는 HTTP 응답 필드가 아니라 단언할 표면이 없다.
// 그래서 의존성 없는 이 필터를 직접 호출 + Logback ListAppender 로 로그 이벤트의 MDC 를 캡처해 검증한다(Spring·DB 없음).
class AccessLogFilterTest {
    private val filter = AccessLogFilter()
    private val logger = LoggerFactory.getLogger(AccessLogFilter::class.java) as Logger
    private val appender =
        ListAppender<ILoggingEvent>().apply {
            start()
            logger.addAppender(this)
        }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
    }

    @Test
    fun `adminActor attribute 가 있으면 access log 의 MDC 에 슬랙명이 실린다`() {
        val request = MockHttpServletRequest("GET", "/admin/metrics")
        request.setAttribute(LoggingKeys.ADMIN_ACTOR, "theo")

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertEquals("theo", appender.list.single().mdcPropertyMap[LoggingKeys.ADMIN_ACTOR])
    }

    @Test
    fun `adminActor attribute 가 없으면 MDC 에 adminActor 가 없다`() {
        val request = MockHttpServletRequest("GET", "/api/v1/users/me")

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertNull(appender.list.single().mdcPropertyMap[LoggingKeys.ADMIN_ACTOR])
    }

    @Test
    fun `userId attribute 도 같은 방식으로 access log MDC 에 실린다 (기존 contract 회귀 가드)`() {
        val request = MockHttpServletRequest("GET", "/api/v1/users/me")
        request.setAttribute(LoggingKeys.USER_ID, "11111111-2222-3333-4444-555555555555")

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        assertEquals("11111111-2222-3333-4444-555555555555", appender.list.single().mdcPropertyMap[LoggingKeys.USER_ID])
    }
}
