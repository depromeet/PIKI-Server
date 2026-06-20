package com.depromeet.piki.notification.fcm.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.notification.fcm.controller.dto.FcmDeviceUnregisterRequest
import com.depromeet.piki.notification.fcm.controller.dto.FcmTokenRegisterRequest
import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.user.domain.IdentityType
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// 토큰 등록/해제(#244) HTTP contract 검증 — 인증·Bean Validation·응답 래퍼 + 핵심 도메인 동작(upsert·reconcile).
// FCM 발송(#245)은 PushNotificationChannelIntegrationTest 가 덮으므로 여기서는 토큰 수집 경계에 집중한다.
// /api/v1/fcm/** 는 authenticated()(GUEST 포함, 권한 검사 없음)라 users row 없이 JWT 만으로 호출한다.
@Transactional
class FcmTokenControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var objectMapper: ObjectMapper

    @Autowired private lateinit var jwtProvider: JwtProvider

    @Autowired private lateinit var userDeviceRepository: UserDeviceRepository

    private fun memberToken(userId: UUID): String = jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun MockMvc.registerToken(
        userId: UUID,
        token: String,
        deviceId: String,
    ) {
        perform(
            post("/api/v1/fcm/tokens")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(FcmTokenRegisterRequest(token = token, deviceId = deviceId))),
        ).andExpect(status().isOk)
    }

    @Test
    fun `인증 유저가 토큰을 등록하면 200 과 함께 기기가 저장된다`() {
        val userId = UUID.randomUUID()

        buildMockMvc()
            .perform(
                post("/api/v1/fcm/tokens")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FcmTokenRegisterRequest(token = "token-1", deviceId = "device-1"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.detail").value("완료했어요."))
            .andExpect(jsonPath("$.data").value(nullValue()))

        val saved = userDeviceRepository.findByUserIdAndDeviceId(userId, "device-1")
        assertNotNull(saved)
        assertEquals("token-1", saved.fcmToken)
    }

    @Test
    fun `token 이 비어 있으면 400 이 ApiResponseBody contract 로 내려간다`() {
        val userId = UUID.randomUUID()

        buildMockMvc()
            .perform(
                post("/api/v1/fcm/tokens")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FcmTokenRegisterRequest(token = "", deviceId = "device-1"))),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail", notNullValue()))
            .andExpect(jsonPath("$.data").value(nullValue()))
    }

    @Test
    fun `토큰 없이 등록 요청하면 401 이 ApiResponseBody contract 로 내려간다`() {
        buildMockMvc()
            .perform(
                post("/api/v1/fcm/tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FcmTokenRegisterRequest(token = "token-1", deviceId = "device-1"))),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail", notNullValue()))
    }

    @Test
    fun `같은 기기로 토큰을 다시 등록하면 그 기기의 토큰만 교체된다`() {
        val userId = UUID.randomUUID()
        val mvc = buildMockMvc()

        mvc.registerToken(userId, token = "token-old", deviceId = "device-1")
        mvc.registerToken(userId, token = "token-new", deviceId = "device-1")

        val devices = userDeviceRepository.findAllByUserId(userId)
        assertEquals(1, devices.size)
        assertEquals("token-new", devices.first().fcmToken)
    }

    @Test
    fun `다른 유저가 같은 토큰을 등록하면 이전 소유자 기기가 해제된다`() {
        val firstUser = UUID.randomUUID()
        val secondUser = UUID.randomUUID()
        val mvc = buildMockMvc()

        mvc.registerToken(firstUser, token = "shared-token", deviceId = "device-1")
        mvc.registerToken(secondUser, token = "shared-token", deviceId = "device-2")

        assertTrue(userDeviceRepository.findAllByUserId(firstUser).isEmpty())
        val holder = userDeviceRepository.findByFcmToken("shared-token")
        assertNotNull(holder)
        assertEquals(secondUser, holder.userId)
    }

    @Test
    fun `인증 유저가 기기를 해제하면 200 과 함께 기기가 삭제된다`() {
        val userId = UUID.randomUUID()
        val mvc = buildMockMvc()
        mvc.registerToken(userId, token = "token-1", deviceId = "device-1")

        mvc
            .perform(
                delete("/api/v1/fcm/tokens")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FcmDeviceUnregisterRequest(deviceId = "device-1"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.detail").value("완료했어요."))

        assertTrue(userDeviceRepository.findAllByUserId(userId).isEmpty())
    }

    @Test
    fun `없는 기기를 해제해도 200 으로 멱등 처리된다`() {
        val userId = UUID.randomUUID()

        buildMockMvc()
            .perform(
                delete("/api/v1/fcm/tokens")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${memberToken(userId)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FcmDeviceUnregisterRequest(deviceId = "ghost-device"))),
            ).andExpect(status().isOk)
    }

    @Test
    fun `토큰 없이 해제 요청하면 401 이 내려간다`() {
        buildMockMvc()
            .perform(
                delete("/api/v1/fcm/tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FcmDeviceUnregisterRequest(deviceId = "device-1"))),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail", notNullValue()))
    }
}
