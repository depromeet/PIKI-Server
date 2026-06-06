package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.notification.fcm.controller.dto.DevPushRequest
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.StubFcmMessageSender
import com.depromeet.piki.user.domain.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
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
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import kotlin.test.assertEquals

// 개발 전용 발송 엔드포인트(@Profile("!prod")) contract. 테스트 프로파일은 prod 가 아니라 빈이 로드된다.
// 외부 FCM 은 StubFcmMessageSender(@Primary)로 격리 — 본문 토큰이 그대로 sender 로 넘어가는지 확인한다.
@Transactional
class DevFcmControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var jwtProvider: JwtProvider

    @Autowired private lateinit var stubFcmMessageSender: StubFcmMessageSender

    private fun guestToken(userId: UUID): String = jwtProvider.generateAccessToken(userId, IdentityType.GUEST)

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    @Test
    fun `GUEST 가 토큰을 지정해 발송하면 200 과 함께 그 토큰으로 발송된다`() {
        val userId = UUID.randomUUID()
        var captured: List<String>? = null
        stubFcmMessageSender.onSend = { tokens, _ ->
            captured = tokens
            emptyList()
        }

        buildMockMvc()
            .perform(
                post("/api/v1/dev/fcm/push")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${guestToken(userId)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(DevPushRequest(token = "live-token-from-xcode"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.fcmEnabled").value(true))
            .andExpect(jsonPath("$.data.staleTokenCount").value(0))

        assertEquals(listOf("live-token-from-xcode"), captured)
    }

    @Test
    fun `미인증으로 발송 요청하면 401 이 내려간다`() {
        buildMockMvc()
            .perform(
                post("/api/v1/dev/fcm/push")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(DevPushRequest(token = "t"))),
            ).andExpect(status().isUnauthorized)
    }
}
