package com.depromeet.piki.metrics.launch

import com.depromeet.piki.support.IntegrationTestSupport
import com.depromeet.piki.support.uuidToBytes
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

// Part B — 런칭 경계(KST→UTC 변환) 전/후 분리와 신규 user_daily_activity 리텐션 조인을 실데이터 + Testcontainers
// MySQL 로 검증한다. created_at 은 UTC 저장이라, 경계 6/20 00:00 KST = 6/19 15:00 UTC 를 기준으로 14:00(전)·16:00(후)
// 유저를 심어 분리가 맞는지 본다. 시딩하지 않은 지표(토너먼트·푸시 등)는 0 으로 떨어져 쿼리 실행 무결성도 함께 확인한다.
// admin 게이트는 IntegrationTestSupport 의 admin.local-bypass 로 우회된다.
@Transactional
class LaunchMetricsRecapIntegrationTest : IntegrationTestSupport() {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private fun mockMvc() =
        MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

    private val beforeBoundary = "2026-06-19 14:00:00" // < 6/19 15:00 UTC (= 6/20 00:00 KST)
    private val afterBoundary = "2026-06-19 16:00:00" // 런칭 후 + 런칭 당일 윈도우 내

    private fun insertUser(
        id: UUID,
        identityType: String,
        createdAt: String,
    ) = jdbcTemplate.update(
        "INSERT INTO users (id, nickname, identity_type, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
        uuidToBytes(id),
        id.toString().take(10),
        identityType,
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt),
    )

    @Test
    fun `런칭 경계 전후 가입자와 리텐션이 정확히 집계되고 미시딩 지표는 0으로 실행된다`() {
        // 런칭 전 활성 유저 2
        repeat(2) { insertUser(UUID.randomUUID(), "MEMBER", beforeBoundary) }

        // 런칭 후 유저 3 (회원 2 · 게스트 1)
        val member1 = UUID.randomUUID()
        val member2 = UUID.randomUUID()
        val guest1 = UUID.randomUUID()
        insertUser(member1, "MEMBER", afterBoundary)
        insertUser(member2, "MEMBER", afterBoundary)
        insertUser(guest1, "GUEST", afterBoundary)

        // 소셜 연결(provider) — 가입과 거의 동시각이라 전환(1분 이상 갭) 아님
        jdbcTemplate.update(
            "INSERT INTO user_details (user_id, email, provider, social_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            uuidToBytes(member1), "a@t.com", "KAKAO", UUID.randomUUID().toString(), Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )
        jdbcTemplate.update(
            "INSERT INTO user_details (user_id, email, provider, social_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            uuidToBytes(member2), "b@t.com", "GOOGLE", UUID.randomUUID().toString(), Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )

        // 위시 — URL 아이템 1 · 이미지(source_url NULL) 아이템 1
        jdbcTemplate.update(
            "INSERT INTO items (id, source_url, created_at, updated_at) VALUES (?, ?, ?, ?)",
            1001L, "https://shop.example/a", Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )
        jdbcTemplate.update(
            "INSERT INTO items (id, created_at, updated_at) VALUES (?, ?, ?)",
            1002L, Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )
        // 파싱 결과 스냅샷 — READY 1(item 1001) · FAILED 1(item 1002). wish 는 이 snapshot 을 가리킨다.
        jdbcTemplate.update(
            "INSERT INTO item_snapshots (id, item_id, status, created_at, updated_at) VALUES (2001, 1001, 'READY', ?, ?)",
            Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )
        jdbcTemplate.update(
            "INSERT INTO item_snapshots (id, item_id, status, created_at, updated_at) VALUES (2002, 1002, 'FAILED', ?, ?)",
            Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )
        // wish 는 item_id 가 아니라 snapshot_id 로 참조(정규화됨)
        jdbcTemplate.update(
            "INSERT INTO wishes (user_id, snapshot_id, created_at, updated_at) VALUES (?, 2001, ?, ?)",
            uuidToBytes(member1), Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )
        jdbcTemplate.update(
            "INSERT INTO wishes (user_id, snapshot_id, created_at, updated_at) VALUES (?, 2002, ?, ?)",
            uuidToBytes(member1), Timestamp.valueOf(afterBoundary), Timestamp.valueOf(afterBoundary),
        )

        // 리텐션 — 런칭날 가입자 3명 중 2명이 다음날(6/21 KST) 활동
        jdbcTemplate.update("INSERT INTO user_daily_activity (user_id, active_date, created_at) VALUES (?, ?, NOW(6))", uuidToBytes(member1), Date.valueOf(LocalDate.of(2026, 6, 21)))
        jdbcTemplate.update("INSERT INTO user_daily_activity (user_id, active_date, created_at) VALUES (?, ?, NOW(6))", uuidToBytes(member2), Date.valueOf(LocalDate.of(2026, 6, 21)))

        val result =
            mockMvc()
                .perform(get("/admin/metrics/launch").param("date", "2026-06-20"))
                .andExpect(status().isOk)
                .andExpect(view().name("admin/launch-recap"))
                .andReturn()

        val recap = result.modelAndView!!.model["recap"] as LaunchRecap

        // 가입 — 경계 전후 분리
        assertEquals(2, recap.signup.before)
        assertEquals(3, recap.signup.after)
        assertEquals(2, recap.signup.afterMembers)
        assertEquals(1, recap.signup.afterGuests)
        assertEquals(1L, recap.signup.byProvider["KAKAO"])
        assertEquals(1L, recap.signup.byProvider["GOOGLE"])
        assertEquals(0, recap.signup.guestToMemberConversions)

        // 위시
        assertEquals(2, recap.wish.total)
        assertEquals(1, recap.wish.fromUrl)
        assertEquals(1, recap.wish.fromImage)
        assertEquals(1, recap.wish.parsedReady)
        assertEquals(1, recap.wish.parsedFailed)
        assertEquals(50, recap.wish.parseSuccessRate)

        // 리텐션
        assertEquals(3, recap.retention.launchDaySignups)
        assertEquals(2, recap.retention.d1Returned)
        assertEquals(66, recap.retention.d1Rate)

        // 미시딩 지표 — 쿼리 실행 무결성(0)
        assertEquals(0, recap.tournament.created)
        assertEquals(0, recap.tournament.itemsAdded)
        assertEquals(0, recap.pushReachableUsers)
        assertEquals(0, recap.push.notificationsTotal)
        assertEquals(0, recap.push.deliverySuccess)
    }
}
