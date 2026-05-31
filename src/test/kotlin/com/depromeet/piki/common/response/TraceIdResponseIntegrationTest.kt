package com.depromeet.piki.common.response

import com.depromeet.piki.common.web.TraceIdHeaderFilter
import com.depromeet.piki.support.IntegrationTestSupport
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// 운영 서블릿 경로에는 Micrometer Tracing 의 ServerHttpObservationFilter 가 요청마다 trace 스코프를 열어
// MDC(traceId)를 채우고, TraceIdHeaderFilter 가 그 값을 X-Trace-Id 응답 헤더로 내보낸다. MockMvc 의
// webAppContextSetup 은 그 관측 필터를 자동 포함하지 않으므로, 여기서는 Tracer 로 trace 스코프를 직접 열고
// TraceIdHeaderFilter 를 체인에 얹어 "스코프가 열린 요청의 응답 헤더에 그 traceId 가 실린다"는 contract 를
// 그 값까지 회귀 가드한다.
@Transactional
class TraceIdResponseIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var tracer: Tracer

    @Test
    fun `trace 스코프가 열린 요청의 응답 헤더에 그 traceId 가 실린다`() {
        val mockMvc: MockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters<DefaultMockMvcBuilder>(TraceIdHeaderFilter())
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        val span = tracer.nextSpan().name("test-request").start()
        val expectedTraceId = span.context().traceId()
        // MockMvc 요청은 호출 스레드에서 동기로 실행되므로, 이 스코프 안에서 보내면 TraceIdHeaderFilter 가
        // 같은 스레드의 MDC(traceId)를 읽어 헤더에 박는다 — 운영에서 관측 필터가 여는 스코프와 동일한 효과.
        tracer.withSpan(span).use {
            mockMvc
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app"))
                .andExpect(status().isCreated)
                .andExpect(header().string(TraceIdHeaderFilter.HEADER, expectedTraceId))
        }
        span.end()
    }
}
