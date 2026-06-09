package com.depromeet.piki.notification.controller

import com.depromeet.piki.auth.infrastructure.jwt.JwtProvider
import com.depromeet.piki.notification.controller.dto.NotificationReadRequest
import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationRouting
import com.depromeet.piki.notification.domain.NotificationType
import com.depromeet.piki.notification.repository.NotificationJpaRepository
import com.depromeet.piki.notification.repository.NotificationRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Transactional
class NotificationHistoryControllerIntegrationTest : IntegrationTestSupport() {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    @Autowired private lateinit var jwtProvider: JwtProvider

    @Autowired private lateinit var notificationRepository: NotificationRepository

    @Autowired private lateinit var notificationJpaRepository: NotificationJpaRepository

    private fun authHeader(userId: UUID): String = "Bearer ${jwtProvider.generateAccessToken(userId, IdentityType.MEMBER)}"

    private fun buildMockMvc(): MockMvc =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private fun seed(
        userId: UUID,
        isRead: Boolean = false,
        routing: NotificationRouting? = null,
    ): Long {
        val saved =
            notificationRepository.save(
                Notification(userId, NotificationType.ITEM_PARSING_COMPLETED, "제목", "본문", 11L, routing),
            )
        if (isRead) {
            saved.markRead()
            notificationRepository.save(saved)
        }
        return saved.getId()
    }

    @Test
    fun `본인 알림만 최신순으로 unreadCount 와 함께 조회된다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val oldest = seed(userId, isRead = true)
        val middle = seed(userId, isRead = false, routing = NotificationRouting.Tournament(99L, 555L))
        val newest = seed(userId, isRead = false, routing = NotificationRouting.Wish)
        seed(otherUserId, isRead = false) // 타인 알림 — 결과에 섞이면 안 됨

        buildMockMvc()
            .perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, authHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(3))
            // 최신순(id desc): newest → middle → oldest
            .andExpect(jsonPath("$.data.items[0].id").value(newest))
            .andExpect(jsonPath("$.data.items[0].kind").value("WISH"))
            .andExpect(jsonPath("$.data.items[1].id").value(middle))
            .andExpect(jsonPath("$.data.items[1].kind").value("TOURNAMENT"))
            .andExpect(jsonPath("$.data.items[1].tournamentId").value(99))
            .andExpect(jsonPath("$.data.items[1].tournamentItemId").value(555))
            .andExpect(jsonPath("$.data.items[2].id").value(oldest))
            .andExpect(jsonPath("$.data.items[2].isRead").value(true))
            .andExpect(jsonPath("$.data.unreadCount").value(2))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value(nullValue()))
    }

    @Test
    fun `size 로 페이지를 나누고 nextCursor 로 다음 페이지를 잇는다`() {
        val userId = UUID.randomUUID()
        val first = seed(userId)
        val second = seed(userId)
        val third = seed(userId)
        val mockMvc = buildMockMvc()

        // 1페이지: size=2 → 최신 2건(third, second), 다음 페이지 있음, 커서=second
        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].id").value(third))
            .andExpect(jsonPath("$.data.items[1].id").value(second))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(true))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value(second.toString()))

        // 2페이지: cursor=second → 남은 1건(first), 다음 페이지 없음
        mockMvc
            .perform(
                get("/api/v1/notifications")
                    .param("size", "2")
                    .param("cursor", second.toString())
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].id").value(first))
            .andExpect(jsonPath("$.pageResponse.hasNext").value(false))
            .andExpect(jsonPath("$.pageResponse.nextCursor").value(nullValue()))
    }

    @Test
    fun `유효하지 않은 cursor 는 400 으로 거른다`() {
        val userId = UUID.randomUUID()
        buildMockMvc()
            .perform(
                get("/api/v1/notifications")
                    .param("cursor", "abc")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail", notNullValue()))
    }

    @Test
    fun `토큰 없이 조회하면 401 이 ApiResponseBody contract 로 내려간다`() {
        buildMockMvc()
            .perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail", notNullValue()))
    }

    @Test
    fun `read all 은 본인 안읽음을 전부 읽음 처리하고 unreadCount 가 0 이 된다`() {
        val userId = UUID.randomUUID()
        seed(userId, isRead = false)
        seed(userId, isRead = false)
        val mockMvc = buildMockMvc()

        mockMvc
            .perform(
                post("/api/v1/notifications/read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"all":true}""")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)

        mockMvc
            .perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, authHeader(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.unreadCount").value(0))
            .andExpect(jsonPath("$.data.items[0].isRead").value(true))
            .andExpect(jsonPath("$.data.items[1].isRead").value(true))
    }

    @Test
    fun `read ids 는 지정한 본인 알림만 읽음 처리하고 타인 알림은 무영향이다`() {
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val target = seed(userId, isRead = false)
        val untouched = seed(userId, isRead = false)
        val others = seed(otherUserId, isRead = false)

        buildMockMvc()
            .perform(
                post("/api/v1/notifications/read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ids":[$target,$others]}""")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isOk)

        // 지정 + 본인 소유만 read. 미지정 본인 것·타인 것은 그대로(소유 검증은 쿼리 user_id 가 겸한다).
        assertTrue(notificationJpaRepository.findById(target).get().isRead)
        assertFalse(notificationJpaRepository.findById(untouched).get().isRead)
        assertFalse(notificationJpaRepository.findById(others).get().isRead)
    }

    @Test
    fun `all 과 ids 를 함께 보내면 400 이고 detail 에 위반 메시지가 실린다`() {
        val userId = UUID.randomUUID()
        buildMockMvc()
            .perform(
                post("/api/v1/notifications/read")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"all":true,"ids":[1]}""")
                    .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("validSelection: ${NotificationReadRequest.VALID_SELECTION_MESSAGE}"))
    }

    @Test
    fun `read 는 멱등이라 이미 읽은 알림에 다시 보내도 200 이다`() {
        val userId = UUID.randomUUID()
        seed(userId, isRead = false)
        val mockMvc = buildMockMvc()

        repeat(2) {
            mockMvc
                .perform(
                    post("/api/v1/notifications/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"all":true}""")
                        .header(HttpHeaders.AUTHORIZATION, authHeader(userId)),
                ).andExpect(status().isOk)
        }

        mockMvc
            .perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, authHeader(userId)))
            .andExpect(jsonPath("$.data.unreadCount").value(0))
    }
}
