package com.depromeet.piki.common.response

import com.depromeet.piki.support.IntegrationTestSupport
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext

// 운영 서블릿 경로에는 Micrometer Tracing 이 요청마다 trace 스코프를 열어 MDC(traceId)를 채우고,
// ApiResponseBody 가 그 값을 응답 traceId 로 싣는다. MockMvc 의 webAppContextSetup 은 그 관측 필터를
// 자동 포함하지 않아(일반 통합테스트의 응답 traceId 는 null) 운영 흐름을 그대로 재현하기 어렵다.
// 그래서 여기서는 Tracer 로 trace 스코프를 직접 연 뒤 요청을 보내, "스코프가 열린 요청의 응답에는
// 그 traceId 가 그대로 실린다"는 contract 를 그 값까지 회귀 가드한다.
//
// 게스트 생성 요청이 DB 에 남으면 다른 테스트의 "유저 없음" 시나리오를 깨므로, 다른 통합테스트와 동일하게
// 클래스 레벨 @Transactional 자동 롤백으로 격리한다(응답은 롤백 전 값이라 traceId 검증엔 영향 없다).
@Transactional
class TraceIdResponseIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var tracer: Tracer

    @Test
    fun `trace 스코프가 열린 요청의 응답에는 그 traceId 가 실린다`() {
        val mockMvc: MockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        val span = tracer.nextSpan().name("test-request").start()
        val expectedTraceId = span.context().traceId()
        // MockMvc 요청은 호출 스레드에서 동기로 DispatcherServlet 을 실행하므로, 이 스코프 안에서 보내면
        // 컨트롤러가 같은 스레드의 MDC(traceId)를 본다 — 운영에서 관측 필터가 여는 스코프와 동일한 효과.
        tracer.withSpan(span).use {
            mockMvc
                .perform(post("/api/v1/auth/guest").contentType(MediaType.APPLICATION_JSON).header("X-Client-Type", "app"))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.traceId").value(expectedTraceId))
        }
        span.end()
    }
}
