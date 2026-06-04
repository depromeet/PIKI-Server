package com.depromeet.piki.common.controller

import com.depromeet.piki.support.IntegrationTestSupport
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

class ActuatorIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Test
    fun `GET actuator health - 인증 없이 200, status UP 이 와야 한다 (Alloy scrape 가드)`() {
        // EC2 내부 Grafana Alloy 가 인증 없이 localhost 로 scrape 해야 한다.
        // SecurityConfig 의 permitAll 이 누락되면 401 이 되어 수집이 끊긴다.
        // actuator health 응답은 ApiResponseBody 래퍼가 아닌 actuator 고유 포맷이라
        // $.status 는 우리 API 의 숫자 코드가 아니라 문자열 "UP" 이다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `GET actuator prometheus - 인증 없이 200, JVM 메트릭 텍스트가 노출돼야 한다`() {
        // micrometer-registry-prometheus 가 클래스패스에 있고 prometheus 엔드포인트가
        // exposure.include 에 포함돼야 텍스트 포맷 메트릭이 노출된다.
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("jvm_")))
    }

    @Test
    fun `GET actuator loggers - 인증 없이 200, 런타임 로그 레벨 변경 엔드포인트가 노출돼야 한다`() {
        // 평소 DEBUG 인 비-API 인증 로그를 조사 시 런타임에 켜려면 loggers 가 exposure.include + permitAll 이어야 한다.
        // (외부 도달은 nginx 가 /actuator/ 403 으로 차단하므로, permitAll 은 localhost 한정 도달을 전제로 한다.)
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/actuator/loggers"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("DEBUG")))
    }

    @Test
    fun `GET actuator metrics - 미노출 경로라 인증 없이 접근 시 401 (노출 정책 회귀 가드)`() {
        // metrics 엔드포인트는 application.yml exposure.include 에서 제외했고 SecurityConfig
        // permitAll 에도 없다. permitAll 이 아니므로 anyRequest().authenticated() 에 걸려 인증 없이는 401.
        //
        // 주의 — 이 401 은 "permitAll 아님"만 증명한다. Security 필터가 DispatcherServlet 보다 먼저라
        // 엔드포인트 등록 여부와 무관하게 401 이 난다. 따라서 누군가 metrics 를 exposure 에 노출하되
        // permitAll 을 빠뜨리면 여전히 401 이라 이 가드는 못 잡는다. 이 가드가 실제로 막는 회귀는
        // "노출 + permitAll 까지 돼 외부에 열리는" 경우다 (그 조합이면 200 이 되어 단언이 깨진다).
        val mockMvc =
            MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(springSecurity())
                .build()

        mockMvc
            .perform(get("/actuator/metrics"))
            .andExpect(status().isUnauthorized)
    }
}
