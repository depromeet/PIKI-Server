package com.depromeet.piki.common.monitoring

import io.sentry.Hint
import io.sentry.SentryEvent
import io.sentry.protocol.Request
import org.junit.jupiter.api.AfterEach
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SentryContextEventProcessorTest {
    private val processor = SentryContextEventProcessor()

    // MDC·RequestContextHolder 는 thread-local 전역이라 테스트가 남긴 값이 다음으로 새지 않게 정리한다
    // (fixture 사전 셋업이 아니라 전역 누수 차단).
    @AfterEach
    fun cleanup() {
        MDC.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    private fun bindRequest(request: MockHttpServletRequest) =
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    @Test
    fun `MDC traceId 를 trace_id 태그로 올려 Tempo 피벗을 가능케 한다`() {
        MDC.put("traceId", "abc123def456")

        val event = processor.process(SentryEvent(), Hint())

        assertEquals("abc123def456", event.getTag("trace_id"))
    }

    @Test
    fun `전부 0 인 noop traceId 는 유효하지 않아 태그로 올리지 않는다`() {
        MDC.put("traceId", "00000000000000000000000000000000")

        val event = processor.process(SentryEvent(), Hint())

        assertNull(event.getTag("trace_id"))
    }

    @Test
    fun `traceId 가 MDC 에 없으면 태그를 붙이지 않는다`() {
        val event = processor.process(SentryEvent(), Hint())

        assertNull(event.getTag("trace_id"))
    }

    @Test
    fun `MDC userId 를 Sentry user id 로 싣는다`() {
        MDC.put("userId", "11111111-2222-3333-4444-555555555555")

        val event = processor.process(SentryEvent(), Hint())

        assertEquals("11111111-2222-3333-4444-555555555555", event.user?.id)
    }

    @Test
    fun `MDC adminActor 를 admin_actor 태그로 싣는다`() {
        MDC.put("adminActor", "조재중")

        val event = processor.process(SentryEvent(), Hint())

        assertEquals("조재중", event.getTag("admin_actor"))
    }

    @Test
    fun `요청 URL 쿼리스트링은 토큰 누출 방지로 제거하고 path 만 남긴다`() {
        val event =
            SentryEvent().apply {
                request =
                    Request().apply {
                        url = "https://api.piki.day/api/v1/wishlists?token=secret&page=2"
                        queryString = "token=secret&page=2"
                    }
            }

        val processed = processor.process(event, Hint())

        assertEquals("https://api.piki.day/api/v1/wishlists", processed.request?.url)
        assertNull(processed.request?.queryString)
    }

    @Test
    fun `endpoint 태그를 라우트 템플릿(method + best pattern)으로 싣어 구체 경로가 흩어지지 않게 묶는다`() {
        val request = MockHttpServletRequest("POST", "/api/v1/wishlists/1")
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/wishlists/{id}")
        bindRequest(request)

        val event = processor.process(SentryEvent(), Hint())

        assertEquals("POST /api/v1/wishlists/{id}", event.getTag("endpoint"))
    }

    @Test
    fun `best pattern 이 없으면(핸들러 미매칭) endpoint 는 requestURI 로 폴백한다`() {
        bindRequest(MockHttpServletRequest("GET", "/unknown"))

        val event = processor.process(SentryEvent(), Hint())

        assertEquals("GET /unknown", event.getTag("endpoint"))
    }

    @Test
    fun `provider path 변수가 있으면 provider 태그로 싣어 소셜별 실패를 가른다`() {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/login/kakao")
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("provider" to "kakao"))
        bindRequest(request)

        val event = processor.process(SentryEvent(), Hint())

        assertEquals("kakao", event.getTag("provider"))
    }

    @Test
    fun `provider path 변수가 없으면 provider 태그를 붙이지 않는다`() {
        bindRequest(MockHttpServletRequest("GET", "/api/v1/wishlists"))

        val event = processor.process(SentryEvent(), Hint())

        assertNull(event.getTag("provider"))
    }

    @Test
    fun `요청 컨텍스트가 없으면(비 HTTP 이벤트) endpoint·provider 태그가 없다`() {
        val event = processor.process(SentryEvent(), Hint())

        assertNull(event.getTag("endpoint"))
        assertNull(event.getTag("provider"))
    }
}
